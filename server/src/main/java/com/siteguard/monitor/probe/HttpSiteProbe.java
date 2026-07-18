package com.siteguard.monitor.probe;

import com.siteguard.monitor.entity.CheckStatus;
import com.siteguard.site.entity.Site;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.KeyStore;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

/// 基于 JDK 11+ java.net.http.HttpClient 的实现。
///
/// 行为约定：
/// - GET 请求
/// - 15 秒超时
/// - 默认 follow redirects（HttpClient.Redirect.NORMAL）
/// - 2xx / 3xx 计为 UP（含 302 等重定向，比如 SSO 跳转）
/// - 4xx / 5xx 计为 DOWN
/// - HttpTimeoutException 计为 TIMEOUT（5s）
/// - SSLHandshakeException：strict 失败后按 [CertForgiveType] 分级处理（见 probeLenient）
/// - 其他异常（IOException / InterruptedException 等）计为 ERROR
///
/// HTTPS 站点会顺手捕获对端证书（首张），提取到期时间和签发机构填入 ProbeResult。
/// java.net.http.HttpClient 不暴露 SSLSession，所以用一个 CapturingTrustManager 在握手阶段把证书链记下来。
///
/// 并发：JDK HttpClient 的 SSL 握手跑在 selector 线程上，跟发起 send() 的调用线程不同；
/// 因此 HTTPS 站点每次 probe 都会创建独立的 CapturingTrustManager + HttpClient，避免并发探活之间互相覆盖证书。
/// HTTP 站点复用共享的 plain HttpClient。
///
/// 该实现是 probe 唯一可能抛出网络异常的入口；本类必须吞掉所有异常并转成 ERROR 结果。
@Component
@Slf4j
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

    /// 构造一个"信任一切"却仍捕获对端证书链的 TrustManager —— 用于 lenient 二次探测。
    /// 认证全部放行（握手一定成功），但 CapturingTrustManager 仍然把链记下来供分类使用。
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
        var url = URI.create(site.getUrl());
        var start = System.currentTimeMillis();
        var request = HttpRequest.newBuilder()
                .uri(url)
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        boolean isHttps = "https".equalsIgnoreCase(url.getScheme());
        HttpClient client = httpClient;
        // JDK HttpClient 不暴露 server cert -- 通过 CapturingTrustManager 在 checkServerTrusted 里顺手记一笔。
        // 注意：证书先被 set(capturedChain) 再交给 delegate 校验，delegate 抛错不影响我们手里这张证书。
        CapturingTrustManager tm = null;
        String host = url.getHost();
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
        } catch (SSLHandshakeException e) {
            // strict 握手失败；站点若开启了任一种"证书失败分级放行"，从 CapturingTrustManager 抓到的
            // 对端证书按类型判定（classifyFailure）。注意 tm.clearCapturedChain() 在 finally 跑，但当前
            // catch 在 finally 之前触发，capturedChain 仍然可放心读。
            return handleCertForgive(site, host, tm, e);
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

    /// strict 握手失败后的分级判定。分两步：
    ///
    /// Step 1 — 分类（不重新握手）：JDK Certificate Phase 在被 delegate 拒绝之前已经把对端证书链
    ///   写到默认 tm.capturedChain 里，所以先从主 probe 里的 tm 拿 leaf，决定"哪类失败"。
    ///   这一步即可以做"过期 -> 永远 ERROR"与"按站点开关决定是否放过"的判定。
    ///   未放开的情况很快返回，省掉第二次连。
    ///
    /// Step 2 — 真放行了：再做一次 lenient HTTP 拿真实 HTTP 状态码 / 响应耗时，
    ///   让可用性告警基于真实站点健康（站点 500 不会因证书放过而显示 UP）。
    ///
    /// 注意：主 probe() finally 会主 tm.clearCapturedChain()，Step 1 在 finally 之前拿到 leaf，能读。
    /// Step 2 重新建单独的 lenientClient(+CapturingTrustManager) 发请，读它自己 tm 里的叶子（原文 leaf）。
    private ProbeResult handleCertForgive(Site site, String host, CapturingTrustManager strictTm,
                                          SSLHandshakeException strictException) {
        var leaf = readLeaf(strictTm);
        if (leaf == null) {
            return ProbeResult.error("SSLHandshakeException: " + strictException.getMessage());
        }

        // --- Step 1：按类型判定是否允许放过 ------------------------------------------------
        // 1a. 过期 / 未生效：永远不放，携带退录给 CertExpiryAlertDefinition。
        try {
            leaf.checkValidity();
        } catch (CertificateNotYetValidException | CertificateExpiredException e) {
            log.debug("Site {} cert expired/not-yet-valid: {}", site.getId(), e.getMessage());
            return ProbeResult.expired(e.getMessage(), extractCertInfo(leaf));
        }

        // 1b. 判定类型 + 站点级开关。
        var type = classifyFailure(host, leaf);
        boolean forgiven = switch (type) {
            case DOMAIN_MISMATCH -> site.isForgiveDomainMismatch();
            case SELF_SIGNED -> site.isForgiveSelfSigned();
            case CHAIN_INCOMPLETE -> site.isForgiveChainIncomplete();
        };

        if (!forgiven) {
            log.debug("Site {} cert-forgive type={}, switch-off -> ERROR", site.getId(), type);
            return ProbeResult.error("CERT_" + type.name() + ": " + strictException.getMessage());
        }

        // --- Step 2：真放行了，再做一次 lenient HTTP 拿真实 HTTP 状态码 -----------------
        // trust-all + 关闭端点识别：lenient 路由不做主机名 / 签名 / 有效期校验，
        // 目的不是“信任”，而是得到一次真实的 HTTP 状态码。站点真挂了 / 500 不会因证书放过显示 UP。
        log.debug("Site {} cert-forgive type={}, switch-on -> lenient GET", site.getId(), type);
        return executeLenientGet(site, leaf);
    }

    /// lenient 二次 HTTP 探活：信任全部 + 关闭主机名验证（真实 httpStatus）。
    ///
    /// 使用 [HttpsURLConnection] 而不是 HttpClient，是因为 JDK HttpClient 对 endpoint identification
    /// 的开关语义长期不稳定——setEndpointIdentificationAlgorithm(null) / ("") 在部分 JDK 下仍做校验；
    /// HttpsURLConnection 则走 HttpsURLConnection#setHostnameVerifier(noop)，关闭主机名验证明确、跨 JDK 稳定。
    private ProbeResult executeLenientGet(Site site, X509Certificate knownLeaf) {
        URL url;
        try {
            url = URI.create(site.getUrl()).toURL();
        } catch (Exception e) {
            return ProbeResult.error("CERT_LENIENT_FAILED[url]: " + e.getMessage());
        }
        long start = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new TrustAllManager()}, null);
            HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
            https.setSSLSocketFactory(sslContext.getSocketFactory());
            https.setHostnameVerifier((hostname, session) -> true);   // noop 主机名校验
            https.setRequestMethod("GET");
            https.setRequestProperty("User-Agent", USER_AGENT);
            https.setConnectTimeout((int) TIMEOUT.toMillis());
            https.setReadTimeout((int) TIMEOUT.toMillis());
            https.setInstanceFollowRedirects(true);
            conn = https;
            // 触发连接 + 握手 + 读取状态码；读 response 关闭以释放
            https.connect();
            int code = https.getResponseCode();
            var elapsed = (int) (System.currentTimeMillis() - start);
            CheckStatus status = (code >= 200 && code < 400) ? CheckStatus.UP : CheckStatus.DOWN;
            var certInfo = extractCertInfo(knownLeaf);
            return new ProbeResult(
                    status, code, elapsed, null,
                    certInfo != null ? certInfo.expiresAt() : null,
                    certInfo != null ? certInfo.issuer() : null
            );
        } catch (Exception e) {
            // lenient 真挂了：区分 connect / read 阶段，便于排障
            String phase = (conn == null) ? "connect" : "read";
            return ProbeResult.error("CERT_LENIENT_FAILED[" + phase + "]: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /// 校验全部放行但不做任何实际检查的 X509TrustManager。用作 lenient 二次探测的 delegate。
    private static final class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // 放行
        }
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // 顺手记 capturedChain（CapturingTrustManager 已做）即可，自身不再校验。
        }
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    /// 分类 strict 握手失败的真实原因。前置条件：leaf 已通过 checkValidity()（未过期）。
    /// 顺序：域名不匹配 > 自签 > 链不完整（兜底）。
    /// 当证书同时满足"域名不匹配"和"自签"时，域名优先（域名错通常是运维关注点，自签是信任决策）。
    static CertForgiveType classifyFailure(String host, X509Certificate leaf) {
        if (!verifyHostname(host, leaf)) {
            return CertForgiveType.DOMAIN_MISMATCH;
        }
        if (isSelfSigned(leaf)) {
            return CertForgiveType.SELF_SIGNED;
        }
        return CertForgiveType.CHAIN_INCOMPLETE;
    }

    /// 主机名校验 JDK 标准语义：SAN 优先，无 SAN 回落到 CN，单星号通配符支持 *.example.com。
    /// 覆盖 [com.siteguard.monitor.probe.HttpSiteProbe#verifyHostname] 测试需要——
    /// 在 probeLenient 中复用，避免另建 SSLSession。
    private static boolean verifyHostname(String host, X509Certificate leaf) {
        // 1. SAN（主体替代名）优先；忽略 missing 异常
        try {
            var sans = leaf.getSubjectAlternativeNames();
            if (sans != null) {
                for (var entry : sans) {
                    if (entry == null || entry.size() < 2) continue;
                    // type 2 = dNSName (RFC 5280)
                    if (Integer.valueOf(2).equals(entry.get(0))) {
                        String san = (String) entry.get(1);
                        if (matchHostName(host, san)) return true;
                    }
                }
                // 有 SAN 但没匹配 → 不再回退到 CN（符合 RFC 6125：SAN present 时 CN 忽略）
                return false;
            }
        } catch (Exception ignored) {
            // SAN 解析异常：回退到 CN 校验
        }
        // 2. 无 SAN（或 SAN 解析失败）→ 检查主体 CN
        var cn = extractCn(leaf.getSubjectX500Principal().getName());
        return cn != null && matchHostName(host, cn);
    }

    /// 单 host 与 pattern 匹配。空串不匹配（RFC 6125 §6.4.3）。
    private static boolean matchHostName(String host, String pattern) {
        if (host == null || pattern == null) return false;
        if (host.isEmpty() || pattern.isEmpty()) return false;
        if (".".equals(pattern)) return false;

        // IP 字面量（IPv4 纯数字段 / 带 [] 的 IPv6）：仅精确匹配，不参与通配
        boolean hostIsIpv4 = !host.contains(":") && host.replace(".", "").chars().allMatch(Character::isDigit);
        if (host.startsWith("[") || hostIsIpv4) {
            return host.equalsIgnoreCase(pattern);
        }

        var h = host.toLowerCase();
        var p = pattern.toLowerCase();
        if (!p.contains("*")) return h.equals(p);

        // 通配符：仅允许开头单段 *.example.com，* 不跨越点
        if (p.startsWith("*.") && p.indexOf('*', 2) == -1) {
            var suffix = p.substring(1); // ".example.com"
            return h.endsWith(suffix) && !h.substring(0, h.length() - suffix.length()).contains(".");
        }
        return h.equals(p);
    }

    /// issuer DN == subject DN 判定为"自签"。
    /// 注意：自签判定独立是否在信任库——一张 CA 签发的证书 issuer ≠ subject，即使 CA 不在信任库也不会误判。
    private static boolean isSelfSigned(X509Certificate leaf) {
        try {
            return leaf.getIssuerX500Principal().equals(leaf.getSubjectX500Principal());
        } catch (Exception e) {
            return false;
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

    private static X509Certificate readLeaf(CapturingTrustManager tm) {
        var chain = tm.getCapturedChain();
        if (chain == null || chain.length == 0) {
            return null;
        }
        return chain[0];
    }

    /// 证书提取结果
    record CertInfo(long expiresAt, String issuer) {}
}
