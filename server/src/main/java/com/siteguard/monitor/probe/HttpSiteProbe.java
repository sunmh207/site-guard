package com.siteguard.monitor.probe;

import com.siteguard.monitor.entity.CheckStatus;
import com.siteguard.site.entity.Site;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

/// 基于 JDK 11+ java.net.http.HttpClient 的实现。
///
/// 行为约定：
/// - GET 请求
/// - 5 秒超时
/// - 默认 follow redirects（HttpClient.Redirect.NORMAL）
/// - 2xx / 3xx 计为 UP（含 302 等重定向，比如 SSO 跳转）
/// - 4xx / 5xx 计为 DOWN
/// - HttpTimeoutException 计为 TIMEOUT（5s）
/// - 其他异常（IOException / InterruptedException 等）计为 ERROR
///
/// HTTPS 站点会顺手捕获对端证书（首张），提取到期时间和签发机构填入 ProbeResult。
/// java.net.http.HttpClient 不暴露 SSLSession，所以用一个 CapturingTrustManager 在
/// 握手阶段把证书链记下来。
///
/// 并发：JDK HttpClient 的 SSL 握手跑在 selector 线程上，跟发起 send() 的调用线程不同；
/// 因此 HTTPS 站点每次 probe 都会创建独立的 CapturingTrustManager + HttpClient，
/// 避免并发探活之间互相覆盖证书。HTTP 站点复用共享的 plain HttpClient。
///
/// 该实现是 probe 唯一可能抛出网络异常的入口；本类必须吞掉所有异常并转成 ERROR 结果。
@Component
public class HttpSiteProbe implements SiteProbe {

    /// 探测超时与连接超时均为 5 秒
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    /// 伪装成 Chrome 浏览器：JDK HttpClient 默认的 "Java/17" UA 容易被站点 WAF 屏蔽。
    /// 固定一个常见 Chrome UA，避免每次请求都暴露"非浏览器"特征。
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36";

    /// HTTP 用的共享 client（无 SSL 配置，开销小）
    private final HttpClient httpClient;

    public HttpSiteProbe() {
        this(null);
    }

    /// 包级可见的构造器：传入 trustStore 让 HTTPS 探活走自定义信任库。
    /// 测试用：让 probe 信任一个测试自签证书。传 null 表示用 JDK 默认信任库。
    HttpSiteProbe(KeyStore trustStore) {
        this.testTrustStore = trustStore;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build();
    }

    private final KeyStore testTrustStore;

    /// 构造一个用默认 JDK 信任库的 CapturingTrustManager。
    static CapturingTrustManager createCapturingTrustManager() {
        return createCapturingTrustManager(null);
    }

    /// 用指定 KeyStore 构造 CapturingTrustManager。传 null 表示用 JDK 默认信任库。
    static CapturingTrustManager createCapturingTrustManager(KeyStore trustStore) {
        try {
            var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            var delegate = (X509TrustManager) tmf.getTrustManagers()[0];
            return new CapturingTrustManager(delegate);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build CapturingTrustManager", e);
        }
    }

    private static HttpClient buildHttpsClient(CapturingTrustManager tm) {
        try {
            var ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] { tm }, null);
            return HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(TIMEOUT)
                    .sslContext(ctx)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build HTTPS HttpClient", e);
        }
    }

    @Override
    public ProbeResult probe(Site site) {
        var urlStr = site.getUrl();
        var url = URI.create(urlStr);
        var start = System.currentTimeMillis();
        var request = HttpRequest.newBuilder()
                .uri(url)
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        boolean isHttps = "https".equalsIgnoreCase(url.getScheme());
        HttpClient client = httpClient;
        CapturingTrustManager tm = null;
        if (isHttps) {
            tm = createCapturingTrustManager(testTrustStore);
            client = buildHttpsClient(tm);
        }

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            var elapsed = (int) (System.currentTimeMillis() - start);
            int code = response.statusCode();
            CheckStatus status = (code >= 200 && code < 400) ? CheckStatus.UP : CheckStatus.DOWN;
            var cert = tm != null ? readCert(tm) : null;
            return new ProbeResult(
                    status,
                    code,
                    elapsed,
                    null,
                    cert != null ? cert.expiresAt() : null,
                    cert != null ? cert.issuer() : null
            );
        } catch (HttpTimeoutException e) {
            return ProbeResult.timeout();
        } catch (InterruptedException e) {
            // 保留中断状态以让上层线程池感知
            Thread.currentThread().interrupt();
            return ProbeResult.error("interrupted: " + e.getMessage());
        } catch (IOException e) {
            return ProbeResult.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            return ProbeResult.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (tm != null) {
                tm.clearCapturedChain();
            }
        }
    }

    /// 从 CapturingTrustManager 拿出对端证书，提取到期时间和签发机构。
    /// 取不到（HTTP 站点 / 握手未发生 / 解析失败）返回 null。
    static CertInfo extractCertInfo(X509Certificate cert) {
        if (cert == null) {
            return null;
        }
        try {
            long expiresAt = cert.getNotAfter().getTime();
            String issuer = extractCn(cert.getIssuerX500Principal().getName());
            return new CertInfo(expiresAt, issuer);
        } catch (RuntimeException e) {
            // 个别畸形证书可能让 getNotAfter / getIssuerX500Principal 抛 NPE 等运行时异常
            return null;
        }
    }

    /// 从 X500 DN 串里抠出 CN 段。找不到时回退到完整 DN 串。
    /// 用 LdapName 而不是字符串 split 是为了正确处理转义逗号、引号值、多值 RDN。
    private static String extractCn(String dn) {
        if (dn == null) {
            return null;
        }
        try {
            var ldapName = new LdapName(dn);
            for (Rdn rdn : ldapName.getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return String.valueOf(rdn.getValue());
                }
            }
        } catch (InvalidNameException ignored) {
            // 非标准 DN：回退到完整串
        }
        return dn;
    }

    private static CertInfo readCert(CapturingTrustManager tm) {
        var chain = tm.getCapturedChain();
        if (chain == null || chain.length == 0) {
            return null;
        }
        return extractCertInfo(chain[0]);
    }

    /// 证书提取结果
    record CertInfo(long expiresAt, String issuer) {}
}
