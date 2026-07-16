package com.siteguard.monitor.alert.detection;

import com.siteguard.monitor.alert.Alert;
import com.siteguard.monitor.alert.AlertDefinition;
import com.siteguard.monitor.alert.AlertDefinition.EvalResult;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.monitor.alert.notification.NotificationEvent;
import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.entity.Site;
import com.siteguard.site.repository.SiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;

/// 告警边沿检测服务：每次 tick 拉所有非暂停站点 → 跑全部检测器 →
/// 用"行集合对称差"对每个 (site, kind) 比对旧 bucket 集合与新真值集合，差集事件发
/// NotificationEvent、增删 site_check_state 行。
///
/// 关键不变量：
/// - paused 站点不参与检测、不写 state、不发事件
/// - 同一 bucket 集合重复 tick 静默（交集即沉默）
/// - 首次建站 + 全部 NORMAL 跳过事件（避免"刚建就报恢复"），但仍写 state
/// - 从异常 → NORMAL（恢复）仍发事件
/// - 检测器异常被吞，记日志，下个站点继续
/// - PATH_CHECK：ABNORMAL 时插入 bucket=pathKey 的 state 行；恢复时 DELETE 该行
///   并发 NORMAL 事件，message 含 bucket 路径信息
@Service
public class AlertDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AlertDetectionService.class);

    private final SiteRepository siteRepo;
    private final SiteCheckStateRepository stateRepo;
    private final SitePathRuleRepository pathRuleRepo;
    private final List<AlertDefinition> definitions;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public AlertDetectionService(SiteRepository siteRepo,
                                 SiteCheckStateRepository stateRepo,
                                 SitePathRuleRepository pathRuleRepo,
                                 List<AlertDefinition> definitions,
                                 ApplicationEventPublisher publisher,
                                 Clock clock) {
        this.siteRepo = siteRepo;
        this.stateRepo = stateRepo;
        this.pathRuleRepo = pathRuleRepo;
        this.definitions = definitions;
        this.publisher = publisher;
        this.clock = clock;
    }

    /// Quartz job 入口：跑一轮全量检测。
    ///
    /// @Transactional 必须放在 public 入口方法上，原因有两层：
    /// 1. Spring AOP 基于代理拦截，private 方法和同类内部 this.method() 调用都绕开代理
    ///    ——所以 @Transactional 不能放在 private detectForKind 上
    /// 2. 调用方（AlertDetectionJob）通过 Spring 代理入口调用 detectAll，事务能正常启动
    ///
    /// 事务边界覆盖本轮所有 kind × 所有站点的 DELETE + INSERT，统一提交；
    /// 循环内 per-site try/catch 吞 RuntimeException 不触发回滚，其他站点写依然生效。
    ///
    /// 必需的事务原因：循环里调用了 `@Modifying @Query` 的
    /// `stateRepo.deleteBySiteIdAndAlertKindAndBucketIn`，无事务时抛 TransactionRequiredException，
    /// 被 per-site try/catch 吞后 state 行不会被实际删除，下个 tick 把同一行视为"刚刚恢复"
    /// 再发一条 NORMAL 事件，触发用户报告的"从正常到正常不断发恢复通知"bug。
    @Transactional
    public void detectAll() {
        List<Site> sites = siteRepo.findAll().stream()
                .filter(s -> !s.isPaused())
                .toList();
        if (sites.isEmpty()) {
            return;
        }
        for (AlertDefinition def : definitions) {
            detectForKind(def, sites);
        }
    }

    /// 单个告警维度的检测循环。
    ///
    /// 不加 @Transactional：本方法由 detectAll 直接调用（同类 this 调用），
    /// 加在 private 方法上无效；事务由 public detectAll 启动后整轮检测共享。
    private void detectForKind(AlertDefinition def, List<Site> sites) {
        AlertKind kind = def.kind();

        // 1) 旧集合：siteId → Set<bucket>
        Map<Long, Set<String>> oldBucketsBySite = new HashMap<>();
        for (SiteCheckState s : stateRepo.findByAlertKind(kind)) {
            oldBucketsBySite
                    .computeIfAbsent(s.getId().siteId(), k -> new LinkedHashSet<>())
                    .add(s.getId().bucket());
        }

        // 2) 每个站点跑检测器、算集合差、发事件、写状态
        for (Site site : sites) {
            try {
                Set<EvalResult> evalResults = def.eval(site, clock);
                Set<String> newBuckets = evalResults.stream()
                        .map(EvalResult::bucket)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                Set<String> oldBuckets = oldBucketsBySite.getOrDefault(site.getId(), Set.of());

                Set<String> disappeared = new LinkedHashSet<>(oldBuckets);
                disappeared.removeAll(newBuckets);
                Set<String> appeared = new LinkedHashSet<>(newBuckets);
                appeared.removeAll(oldBuckets);

                boolean noOldState = oldBuckets.isEmpty();
                boolean newHasAnyAbnormal = evalResults.stream()
                        .anyMatch(e -> e.status() == AlertStatus.ABNORMAL);

                // 3) 新增 → ABNORMAL 发事件；NORMAL 永不发事件（"恢复"由 disappeared 段发）
                /// NORMAL 出现（如 W7 恢复到 OK 时的 OK 档位）只是"现在无异常"的快照态，
                /// 不是真正的边沿——真正的恢复语义落在 disappeared 段（哪些异常 bucket 消失了）。
                /// 把重复的 NORMAL 事件压掉，避免 dashboard 同时看到"恢复 W7"+"新建 OK"两条冗余消息。
                for (String bucket : appeared) {
                    EvalResult er = evalResults.stream()
                            .filter(e -> e.bucket().equals(bucket))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "EvalResult missing for appeared bucket " + bucket));
                    if (er.status() == AlertStatus.NORMAL) {
                        // 跳过事件；state 仍由下方 step 5 写
                    } else {
                        publish(site, kind, er.status(), bucket, er.message(), null);
                    }
                }

                // 4) 消失 → 仅当"彻底恢复"才发 NORMAL；非 PATH_CHECK 在 ABNORMAL→ABNORMAL 级变时静默
                for (String bucket : disappeared) {
                    String msg;
                    if (kind == AlertKind.PATH_CHECK) {
                        /// PATH_CHECK 恢复消息带上 rule 的 expected_http_status；
                        /// rule 在异常期间被删则降级为不带期望码，避免强造状态码误导运维
                        msg = pathRuleRepo.findBySiteIdAndPath(site.getId(), bucket)
                                .map(SitePathRule::getExpectedHttpStatus)
                                .map(expected -> "子路由 `" + bucket + "` 已恢复（期望 " + expected + "）")
                                .orElse("子路由 `" + bucket + "` 已恢复");
                    } else if (!newHasAnyAbnormal && !newBuckets.isEmpty()) {
                        /// 守卫 !newBuckets.isEmpty()：避免检测器"暂无可发事件"被误读为"已恢复"。
                        /// 触发场景：AVAILABILITY 的 counter < threshold 累计中、证书/域名尚未探测到 expiresAt
                        /// 等返回空集的情况。这些场景不是真正的恢复，而是"还在观察"，发 NORMAL 会让抖动场景
                        /// 反复触发误报（用户报告的"多个恢复通知无前置异常"）。
                        msg = Alert.recoveryMessage(kind);
                    } else {
                        /// 级变静默或检测器判定"暂无可发事件"时不读 SiteCheckState，
                        /// 避免给监听器一个误导性的持续时长。
                        continue;
                    }

                    /// DB 抖动时降级为 null，让监听器侧用 "—" 兜底，恢复消息本身不阻断。
                    Long abnormalStartedAt;
                    try {
                        abnormalStartedAt = stateRepo.findById(
                                        new SiteCheckStateId(site.getId(), kind.name(), bucket))
                                .map(SiteCheckState::getLastNotifiedAt)
                                .orElse(null);
                    } catch (RuntimeException e) {
                        log.warn("reading SiteCheckState for recovery event failed: site={} kind={} bucket={}",
                                site.getId(), kind, bucket, e);
                        abnormalStartedAt = null;
                    }

                    publish(site, kind, AlertStatus.NORMAL, bucket, msg, abnormalStartedAt);
                }

                // 5) 写状态：DELETE 消失的行 + INSERT / UPDATE 新增的行
                if (!disappeared.isEmpty()) {
                    stateRepo.deleteBySiteIdAndAlertKindAndBucketIn(
                            site.getId(), kind, disappeared);
                }
                for (EvalResult er : evalResults) {
                    if (appeared.contains(er.bucket()) || noOldState) {
                        stateRepo.save(toRow(site.getId(), kind, er));
                    }
                    // 旧集和新集都有的 bucket（交集）静默：不更新行
                }
            } catch (RuntimeException e) {
                log.warn("detector {} failed for site {}", kind, site.getId(), e);
            }
        }
    }

    private void publish(Site site, AlertKind kind, AlertStatus status,
                         String bucket, String message, Long abnormalStartedAt) {
        publisher.publishEvent(new NotificationEvent(
                site.getId(), site.getName(), site.getUrl(),
                kind, status, bucket, message, clock.millis(), abnormalStartedAt));
    }

    private SiteCheckState toRow(Long siteId, AlertKind kind, EvalResult er) {
        long now = clock.millis();
        return SiteCheckState.builder()
                .id(new SiteCheckStateId(siteId, kind.name(), er.bucket()))
                .lastNotifiedAt(now)
                .updatedAt(now)
                .build();
    }
}
