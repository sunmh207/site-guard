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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

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
///   3. 命中某类且站点开启对应开关 → 视为"证书放行"：不清错误计数、不发子路由告警，并记 lastErrorMessage="cert_forgiven:<TYPE>"
///      以便运维在站点详情里追溯；不累计 consecutive_failures。
///      否则走老的错误路径：累计 counter / 写错误消息 → 子路由阈值达阈值后告警。
///
/// 注意：cert_forgive 只作用于"握手层面的问题"，不影响状态码不匹配 / 超时 / 站点宕机等真实失败——
/// 那些失败要让运维知道，因此仍需计数告警。
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
    /// special case — cert_forgive 被命中：我们不清 lastHttpStatus（握手未发生，状态无从得知）也
    /// 不累计错误（因为站点配置该类型放行），只在 lastErrorMessage 写一条追溯性消息，*同时*把
    /// lastHttpStatus 置为规则期望值（让 isFailing 在恢复时正常按 success 处理；置 null 也无所谓，
    /// SitePathRule.isFailing 在 null 判 failure，所以我们走 counter=0 + message 显式说明放行原因）。
    private void writeOutcome(SitePathRule rule, long checkedAt, PathProbeOutcome outcome) {
        rule.setLastCheckedAt(checkedAt);

        if (outcome.certForgiven()) {
            /// 证书放行：不计失败，只在 message 留追溯痕迹；走 SitePathRule.isFailing(rule) 时不 increment。
            rule.setLastHttpStatus(null);   // 握手未发生，没有真实状态码
            rule.setLastErrorMessage("cert_forgiven:" + outcome.certForgivenType());
            /// 关键：放行时不累计计数器，避免站点级 cert_forgive 配置后路径因不相关的握手问题反复报 PATH_CHECK 异常。
            rule.setConsecutiveFailures(0);
            log.debug("path rule {} cert-forgiven {} -> counter reset", rule.getId(), outcome.certForgivenType());
            return;
        }

        /// 普通路径（HTTP 或 正常 HTTPS 握手成功/失败的标准路径）
        rule.setLastHttpStatus(outcome.httpStatus());
        rule.setLastErrorMessage(outcome.errorMessage());

        /// 维护"连续失败次数"计数器：
        /// - 探测失败（status 不匹配 / 无 status）→ counter +1
        /// - 探测成功（status 匹配）→ counter 归零
        /// 失败判定统一用 SitePathRule.isFailing，probe 层与 detector 层口径一致。
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
            return new PathProbeOutcome(resp.statusCode(), null, false, null);
        } catch (HttpTimeoutException e) {
            return new PathProbeOutcome(null, "timeout after 5s", false, null);
        } catch (SSLHandshakeException e) {
            /// 仅当 CapturingTrustManager 已启用时做分类（== 站点有配 cert_forgive）；否则直接暴露老错误消息。
            if (captureTm != null) {
                PathProbeOutcome forgiven = tryForgiveCert(site, uri.getHost(), captureTm, e);
                if (forgiven != null) {
                    return forgiven;
                }
            }
            return new PathProbeOutcome(null, truncate("SSLHandshakeException: " + e.getMessage()), false, null);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new PathProbeOutcome(null,
                    truncate(e.getClass().getSimpleName() + ": " + e.getMessage()), false, null);
        }
        /// 非预期的 RuntimeException 不在 probeOne 吞——让它向上抛出，
        /// 交由 probe() 的外层 try/catch 捕获并 log warn。in-memory rule 状态
        /// （last_* + counter）保留为旧值，下方 saveAll 把旧值持久化，磁盘 counter 自然保持。
    }

    /// 尝试对 SSL 握手失败走 cert_forgive 判定。
    /// - leaf 抓不到 → null（按老错误消息处理）
    /// - 过期 / 未生效 → null（永远不放）
    /// - 站点开关未对应分类 → null（配置者未放行此类型，仍按失败处理；错误消息保留 SSL 原文）
    /// - 站点开关命中 → 返回标记为 "cert_forgiven" 的 PathProbeOutcome，writeOutcome 据此决定放行（不计数、只留追溯消息）
    private PathProbeOutcome tryForgiveCert(Site site, String host, CapturingTrustManager tm,
                                            SSLHandshakeException handshakeException) {
        try {
            var chain = tm.getCapturedChain();
            if (chain == null || chain.length == 0) {
                return null;
            }
            var leaf = (X509Certificate) chain[0];

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

            log.debug("path rule host={} cert type={}, switch-on -> forgiven", host, type);
            /// 放行时返回带 certForgiven 标记的 outcome，lastErrorMessage 在 writeOutcome 里填 "cert_forgiven:<TYPE>"。
            return new PathProbeOutcome(null, null, true, type);
        } catch (Exception e) {
            log.debug("path rule cert-forgive classify error, fall back to error path: {}", e.getMessage());
            return null;
        }
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

    /// 路径规则探测结果。certForgiven=true 时 writer 走放行路径；httpStatus / errorMessage 在放行时为 null
    /// （由 writeOutcome 填 "cert_forgiven:<type>" 替代）。
    private record PathProbeOutcome(Integer httpStatus, String errorMessage,
                                    boolean certForgiven, CertForgiveType certForgivenType) {
    }
}
