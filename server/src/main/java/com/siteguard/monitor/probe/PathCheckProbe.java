package com.siteguard.monitor.probe;

import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.entity.Site;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
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
    /// 测试可注入；生产由 Spring 选无参构造器自己 new 一个 5s 超时、followRedirects=NORMAL 的客户端
    private final HttpClient httpClient;

    /// 生产用构造器：自己 new 一个 5s 超时、followRedirects=NORMAL 的 HttpClient，
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
        long checkedAt = System.currentTimeMillis();
        for (SitePathRule rule : rules) {
            try {
                PathProbeOutcome outcome = probeOne(site.getUrl(), rule);
                rule.setLastCheckedAt(checkedAt);
                rule.setLastHttpStatus(outcome.httpStatus());
                rule.setLastErrorMessage(outcome.errorMessage());

                /// 维护"连续失败次数"计数器：
                /// - 探测失败（status 不匹配 / 无 status）→ counter +1
                /// - 探测成功（status 匹配）→ counter 归零
                /// 失败判定统一用 SitePathRule.isFailing，probe 层与 detector 层口径一致。
                boolean failed = SitePathRule.isFailing(rule);
                int currentCounter = rule.getConsecutiveFailures();
                rule.setConsecutiveFailures(failed ? currentCounter + 1 : 0);
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
            log.warn("save path rule probe state failed for site={}: {}",
                    site.getId(), e.getMessage());
        }
    }

    private PathProbeOutcome probeOne(String siteUrl, SitePathRule rule) {
        URI uri = resolve(siteUrl, rule.getPath());
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        try {
            var resp = httpClient.send(req, BodyHandlers.discarding());
            return new PathProbeOutcome(resp.statusCode(), null);
        } catch (HttpTimeoutException e) {
            return new PathProbeOutcome(null, "timeout after 5s");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new PathProbeOutcome(null, truncate(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
        /// 非预期的 RuntimeException 不在 probeOne 吞——让它向上抛出，
        /// 交由 probe() 的外层 try/catch 捕获并 log warn。in-memory rule 状态
        /// （last_* + counter）保留为旧值，下方 saveAll 把旧值持久化，磁盘 counter 自然保持。
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

    private record PathProbeOutcome(Integer httpStatus, String errorMessage) {}
}
