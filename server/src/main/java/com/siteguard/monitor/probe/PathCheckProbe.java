package com.siteguard.monitor.probe;

import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.entity.Site;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;

/// 站点自定义子路由探测组件。
///
/// 在 SiteCheckServiceImpl.checkOne(site) 末尾调用，对该站点的所有 path rule
/// 各做一次 HTTP GET，把结果回写到规则行的 last_* 字段。
///
/// 不写 site_check_history；只更新规则行自身。理由：
/// 1. 失败时没有 HTTP 状态可入 history（连接失败）
/// 2. 检测层只读最新值，不需要历史序列
/// 3. 站点详情页直接展示"上次探测"即可
///
/// 单条规则探测失败被吞掉（log warn）—— 一条规则坏掉不影响同站其他规则，也不影响主探测流程。
///
/// HTTPS 握手处理（与 HttpSiteProbe 一致，复用同套站点级 cert_forgive 配置）：
///   1. 共享 http client 用于 HTTP（仅普通 TLS 校验）；HTTPS 单独建 TLS Client + CapturingTrustManager。
///   2. 站点开启任一种 cert_forgive 时，HTTPS 走严格握手 → 失败时捕获对端证书链，按 HttpSiteProbe.classifyFailure
///      分类（过期/未生效永远不放，域名错配 / 自签 / 链不完整按站点开关）。
///   3. 命中某类且站点开启对应开关 → 视为"证书层面放行"：仿 HttpSiteProbe 做一次 trust-all 的 lenient
///      二次 GET，拿到子路由真实 HTTP 状态码后走普通 writeOutcome（与期望状态码比对 + 累计 counter /
///      写错误消息）→ 子路由阈值达阈值后告警。
///      lenient 自身失败（站点真挂了/超时）→ 走错误路径：counter 累计，同样告警。
///      否则（未命中开关/过期）走老的错误路径：累计 counter / 写错误消息 → 子路由阈值达阈值后告警。
///
/// 注意：cert_forgive 只作用于"握手层面的问题"，拿到真实状态码后状态码不匹配 / 超时 / 站点宕机等真实失败
/// 仍要让运维知道，因此仍需计数告警。
@Component
@Slf4j
public class PathCheckProbe {

    /// 探测超时 5 秒（与 HttpSiteProbe 一致）
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    /// 伪装成 Chrome 浏览器：JDK HttpClient 默认的 "Java/17" UA 容易被站点 WAF 屏蔽。
    /// 固定一个常见 Chrome UA，避免每次请求都暴露"非浏览器"特征。
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36";

    private final SitePathRuleRepository ruleRepo;
    /// 共享的 plain HTTP client（无 SSL 配置，开销小）
    private final HttpClient httpClient;

    /// 生产用构造器：自己 new 一个 5s 超时、followRedirects=NORMAL 的 plain client，
    /// 不依赖 Spring 容器里的 HttpClient bean（与 HttpSiteProbe / RdapClient 一致）。
    @Autowired
    public PathCheckProbe(SitePathRuleRepository ruleRepo) {
        this(ruleRepo, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build());
    }

    /// 测试用构造器：注入自定义 HttpClient
    PathCheckProbe(SitePathRuleRepository ruleRepo, HttpClient httpClient) {
        this.ruleRepo = ruleRepo;
        this.httpClient = httpClient;
    }

    public void probe(Site site) {
        List<SitePathRule> rules = ruleRepo.findBySiteIdOrderByIdAsc(site.getId());
        if (rules.isEmpty()) {
            return;
        }
        boolean isHttps = isHttpsUrl(site.getUrl());
        long checkedAt = System.currentTimeMillis();
        for (SitePathRule rule : rules) {
            try {
                PathProbeOutcome outcome = probeOne(site, rule, isHttps);
                writeOutcome(rule, checkedAt, outcome);
            } catch (RuntimeException e) {
                /// 单条规则异常不影响其他规则；counter 保持前值（前次累计不归零），
                /// 避免一次意外错误把累计告警归零。setLastXxx / setConsecutiveFailures 都
                /// 在 probeOne 抛异常之前没执行，所以 in-memory state 仍是旧值，
                /// 下方 saveAll 落盘的也是旧值 → 磁盘 counter 自然保持。
                log.warn("path rule probe failed for site={} rule={}: {}",
                        site.getId(), rule.getId(), e.getMessage());
            }
        }
        try {
            ruleRepo.saveAll(rules);
        } catch (RuntimeException e) {
            log.warn("save path route probe state failed for site={}: {}",
                    site.getId(), e.getMessage());
        }
    }

    /// 写入一条路径规则的探测结果 + 维护连续失败计数器。
    ///
    /// 普通路径（HTTP / 正常 HTTPS 握手成功 / lenient 二次 GET 拿到真实状态码 / 各类失败的错误路径）：
    /// 写 lastHttpStatus + lastErrorMessage，再按 SitePathRule.isFailing 维护"连续失败次数"计数器：
    /// - 探测失败（status 不匹配 / 无 status）→ counter +1
    /// - 探测成功（status 匹配）→ counter 归零
    /// 失败判定统一用 SitePathRule.isFailing，probe 层与 detector 层口径一致。
    private void writeOutcome(SitePathRule rule, long checkedAt, PathProbeOutcome outcome) {
        rule.setLastCheckedAt(checkedAt);
        rule.setLastHttpStatus(outcome.httpStatus());
        rule.setLastErrorMessage(outcome.errorMessage());

        boolean failed = SitePathRule.isFailing(rule);
        int currentCounter = rule.getConsecutiveFailures();
        rule.setConsecutiveFailures(failed ? currentCounter + 1 : 0);
    }

    /// 探测单条 path rule。
    /// - HTTP：直接走共享 plain client。
    /// - HTTPS：
    ///     A. 站点未配任何 cert_forgive → 仍走共享 plain client（行为与老版本完全一致）。
    ///     B. 站点配过 cert_forgive → HTTPS client 绑 CapturingTrustManager，握手失败时抓叶子走同 HttpSiteProbe
    ///        的 classifyFailure + 站点开关判定；命中开关则视为放行。
    private PathProbeOutcome probeOne(Site site, SitePathRule rule, boolean isHttps) {
        HttpClient client = httpClient;
        CapturingTrustManager captureTm = null;
        if (isHttps && site.hasAnyCertForgive()) {
            captureTm = HttpSiteProbe.createCapturingTrustManager(null);
            client = buildHttpsCapturingClient(captureTm);
        }

        URI uri = resolve(site.getUrl(), rule.getPath());
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        try {
            var resp = client.send(req, BodyHandlers.discarding());
            return new PathProbeOutcome(resp.statusCode(), null);
        } catch (HttpTimeoutException e) {
            return new PathProbeOutcome(null, "timeout after 5s");
        } catch (SSLHandshakeException e) {
            /// 仅当 CapturingTrustManager 已启用时做分类（== 站点有配 cert_forgive）；否则直接暴露老错误消息。
            if (captureTm != null) {
                PathProbeOutcome forgiven = tryForgiveCert(site, uri, captureTm, e);
                if (forgiven != null) {
                    return forgiven;
                }
            }
            return new PathProbeOutcome(null, truncate("SSLHandshakeException: " + e.getMessage()));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new PathProbeOutcome(null,
                    truncate(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
        /// 非预期的 RuntimeException 不在 probeOne 吞——让它向上抛出，
        /// 交由 probe() 的外层 try/catch 捕获并 log warn。in-memory rule 状态
        /// （last_* + counter）保留为旧值，下方 saveAll 把旧值持久化，磁盘 counter 自然保持。
    }

    /// 尝试对 SSL 握手失败走 cert_forgive 判定。
    /// - leaf 抓不到 → null（按老错误消息处理）
    /// - 过期 / 未生效 → null（永远不放）
    /// - 站点开关未对应分类 → null（配置者未放行此类型，仍按失败处理；错误消息保留 SSL 原文）
    /// - 站点开关命中 → 仿 HttpSiteProbe 做一次 trust-all 的 lenient 二次 GET，拿到子路由真实 HTTP 状态码后
    ///   走普通 writeOutcome（与期望状态码比对 + 累计 counter）；lenient 自身失败（站点真挂了/超时）→ 按错误路径处理。
    private PathProbeOutcome tryForgiveCert(Site site, URI uri, CapturingTrustManager tm,
                                            SSLHandshakeException handshakeException) {
        try {
            var chain = tm.getCapturedChain();
            if (chain == null || chain.length == 0) {
                return null;
            }
            var leaf = (X509Certificate) chain[0];
            String host = uri.getHost();

            try {
                leaf.checkValidity();
            } catch (java.security.cert.CertificateNotYetValidException | java.security.cert.CertificateExpiredException e) {
                log.debug("path rule host={} cert expired/not-yet-valid, never forgiven: {}", host, e.getMessage());
                return null;
            }

            var type = HttpSiteProbe.classifyFailure(host, leaf);
            boolean forgiven = switch (type) {
                case DOMAIN_MISMATCH -> site.isForgiveDomainMismatch();
                case SELF_SIGNED -> site.isForgiveSelfSigned();
                case CHAIN_INCOMPLETE -> site.isForgiveChainIncomplete();
            };
            if (!forgiven) {
                log.debug("path rule host={} cert type={}, switch-off -> not forgiven", host, type);
                return null;
            }

            /// 证书层面已放行，再拿一次子路由真实 HTTP 状态码：站点真挂了/500 不会因证书放行而显示"正常"。
            /// lenient 失败（站点真挂了/超时）→ 走错误路径，counter 累计，告警仍触发。
            log.debug("path rule host={} cert type={}, switch-on -> lenient GET {}", host, type, uri);
            return executeLenientGet(uri);
        } catch (Exception e) {
            log.debug("path rule cert-forgive classify error, fall back to error path: {}", e.getMessage());
            return null;
        }
    }

    /// cert 放行后的 lenient 二次 GET：trust-all + noop 主机名验证，拿到子路由真实 HTTP 状态码。
    /// 与 HttpSiteProbe.executeLenientGet 同构（JDK HttpsURLConnection 关闭主机名验证语义最稳），
    /// 但只关心状态码，供 writeOutcome 走普通比对 + counter 累计。
    /// - 拿到状态码 → (status, null)
    /// - lenient 自身失败（站点真挂了/超时/IO）→ (null, "cert_lenient_failed:[phase]: ...") 走错误路径
    private static PathProbeOutcome executeLenientGet(URI uri) {
        URL url;
        try {
            url = uri.toURL();
        } catch (Exception e) {
            return new PathProbeOutcome(null, "cert_lenient_failed[url]: " + e.getMessage());
        }
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
            return new PathProbeOutcome(code, null);
        } catch (Exception e) {
            String phase = (conn == null) ? "connect" : "read";
            return new PathProbeOutcome(null, "cert_lenient_failed[" + phase + "]: "
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
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { /* 放行 */ }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { /* 放行 */ }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }

    /// 绑定 CapturingTrustManager 的 HTTPS client（严格校验 → 失败时抓叶子）。
    /// 与 HttpSiteProbe 完全同构；JDK default trust store + 自定义 TM 验证链。
    private static HttpClient buildHttpsCapturingClient(CapturingTrustManager tm) {
        try {
            var ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{tm}, null);
            return HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(TIMEOUT)
                    .sslContext(ctx)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build HTTPS capturing HttpClient for path rule", e);
        }
    }

    /// 拼接 site.url 与 rule.path：
    /// - site.url 不带尾斜杠 + path 以 / 开头 → 直接拼
    /// - site.url 带尾斜杠 + path 以 / 开头 → 去重一个 /
    /// - 其他（path 不以 / 开头）→ 在 path 前加 /
    private static URI resolve(String siteUrl, String path) {
        String base = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
        String tail = path.startsWith("/") ? path : "/" + path;
        return URI.create(base + tail);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private static boolean isHttpsUrl(String url) {
        return url != null && url.regionMatches(true, 0, "https://", 0, 8);
    }

    /// 路径规则探测结果：httpStatus 为 null 表示探测失败（握手失败 / 超时 / lenient 自身失败），
    /// errorMessage 给出可读摘要；writeOutcome 统一按 SitePathRule.isFailing 维护 counter。
    private record PathProbeOutcome(Integer httpStatus, String errorMessage) {
    }
}
