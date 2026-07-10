package com.siteguard.monitor.repository;

import com.siteguard.monitor.entity.CheckStatus;
import com.siteguard.monitor.entity.SiteCheckHistory;
import com.siteguard.site.entity.Site;
import com.siteguard.site.entity.SiteStatus;
import com.siteguard.site.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 仪表盘 4 个聚合查询的 Repository 层集成测试。
///
/// - 使用 @DataJpaTest 启动嵌入式 H2 + Flyway + JPA
/// - H2 启用 MySQL 兼容模式 + DATABASE_TO_LOWER，减少 Flyway 迁移中的方言差异
/// - 通过 TestPropertySource 强制 Flyway 跑 schema 迁移，ddl-auto=validate 校验 Entity 与 DB 对齐
/// - 不验证 SQL 本身（Flyway + Repository 接口定义已约束），只验证查询返回结构与业务语义
/// - 生产 SiteGuardApplication 启用了 @EnableJpaAuditing；@DataJpaTest 会自动从
///   @SpringBootApplication 同包及子包扫描到该配置，因此这里无需重复声明。
@DataJpaTest
@TestPropertySource(properties = {
        // 测试专用 Flyway 位置：User / Site / SiteCheckHistory 的 H2 兼容版本
        // （生产迁移含 MySQL 方言：bigint unsigned / ENGINE=InnoDB / Quartz BLOB 等，H2 跑不动）。
        "spring.flyway.locations=classpath:test-db/migration",
        "spring.jpa.hibernate.ddl-auto=validate",
        // H2 嵌入式数据库 + MySQL 兼容模式
        "spring.datasource.url=jdbc:h2:mem:dashboard-agg;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class DashboardAggregationTest {

    @Autowired
    SiteCheckHistoryRepository historyRepo;

    @Autowired
    SiteRepository siteRepo;

    Site site1;
    Site site2;
    Site site3;

    @BeforeEach
    void setUp() {
        historyRepo.deleteAll();
        siteRepo.deleteAll();

        site1 = newSite("site1", SiteStatus.UP);
        site2 = newSite("site2", SiteStatus.DOWN);
        site3 = newSite("site3", SiteStatus.UNKNOWN);
        siteRepo.saveAll(List.of(site1, site2, site3));

        long now = System.currentTimeMillis();
        // site1: 3 个 UP 平均 100ms（窗口内有效样本）
        saveHistory(site1.getId(), now, CheckStatus.UP, 200, 100, null);
        saveHistory(site1.getId(), now, CheckStatus.UP, 200, 100, null);
        saveHistory(site1.getId(), now, CheckStatus.UP, 200, 100, null);
        // site2: 1 个 DOWN 500（非 UP，参与 recentIssues）
        saveHistory(site2.getId(), now, CheckStatus.DOWN, 500, 200, null);
        // site3: 1 个 TIMEOUT（异常但 UP avg 不参与；不应被算入 up count）
        saveHistory(site3.getId(), now, CheckStatus.TIMEOUT, null, 5000, "timeout");
        // 一个 2 小时前的旧 UP（应被 1h 时间窗口过滤掉，不影响 avg）
        saveHistory(site1.getId(), now - 7_200_000L, CheckStatus.UP, 200, 999, null);

        // —— dedup 测试数据（新增）——
        // site1: 3 次 TIMEOUT（递增时间戳），最终只有最新一条应出现
        saveHistory(site1.getId(), now + 30_000L, CheckStatus.TIMEOUT, null, 5000, "timeout-1");
        saveHistory(site1.getId(), now + 40_000L, CheckStatus.TIMEOUT, null, 5000, "timeout-2");
        saveHistory(site1.getId(), now + 50_000L, CheckStatus.TIMEOUT, null, 5000, "timeout-3-latest");
        // site1: 1 次 DOWN（与上面的 TIMEOUT 是不同 status，两条都要保留）
        saveHistory(site1.getId(), now + 55_000L, CheckStatus.DOWN, 503, 300, "down");
        // site3: 1 次更晚的 TIMEOUT，验证"覆盖早期同组条目"语义（保留 "timeout-latest"）
        saveHistory(site3.getId(), now + 60_000L, CheckStatus.TIMEOUT, null, 5000, "timeout-latest");
    }

    private Site newSite(String name, SiteStatus status) {
        var s = new Site();
        s.setName(name);
        s.setUrl("https://" + name + ".example.com");
        s.setAvailabilityStatus(status);
        return s;
    }

    private void saveHistory(long siteId, long checkedAt, CheckStatus status,
                             Integer httpStatus, Integer responseMs, String error) {
        var h = new SiteCheckHistory();
        h.setSiteId(siteId);
        h.setCheckedAt(checkedAt);
        h.setStatus(status);
        h.setHttpStatus(httpStatus);
        h.setResponseMs(responseMs);
        h.setErrorMessage(error);
        historyRepo.save(h);
    }

    @Test
    void totalSites_isThree() {
        assertEquals(3, siteRepo.count());
    }

    /// 注：原 upSites_isOne / downSites_isOne 断言已废弃——
    /// dashboard 摘要分桶改为基于"任意 ABNORMAL 告警"而非 SiteStatus，
    /// 对应的桶转移路径在 SiteHealthClassifierTest 中覆盖（纯单测，更聚焦）。

    @Test
    void avgResponseMs_since1h_returns100() {
        long oneHourAgo = System.currentTimeMillis() - 3_600_000L;
        Double avg = historyRepo.avgResponseMsSince(oneHourAgo);
        assertNotNull(avg);
        assertEquals(100.0, avg, 0.001);
    }

    @Test
    void avgResponseMs_returnsNullWhenNoUpSamplesInWindow() {
        // 查询一个比所有 setup 数据都靠后的时间窗口，没有样本，AVG 为 null
        long future = System.currentTimeMillis() + 86_400_000L;
        Double futureAvg = historyRepo.avgResponseMsSince(future);
        assertNull(futureAvg);
    }

    @Test
    void recentIssues_returnsNonUpOrderedByCheckedAtDesc() {
        var pageable = PageRequest.of(0, 20);
        List<SiteCheckHistory> issues = historyRepo.findRecentIssues(pageable);
        // UP 不应出现；非 UP 全部按 checkedAt DESC
        for (var issue : issues) {
            assertNotEquals(CheckStatus.UP, issue.getStatus());
        }
        for (int i = 1; i < issues.size(); i++) {
            assertTrue(issues.get(i - 1).getCheckedAt() >= issues.get(i).getCheckedAt(),
                    "issues must be ordered by checkedAt DESC");
        }
    }

    /// 回归：每个 (site_id, status) 仅保留最新一条。
    /// 数据构造（按时间从晚到早）：
    /// - site3 TIMEOUT @ now + 60_000  → "timeout-latest"（覆盖 site3 @ now 的 "timeout"）
    /// - site1 DOWN     @ now + 55_000  → "down"
    /// - site1 TIMEOUT  @ now + 50_000  → "timeout-3-latest"（覆盖 +30s/+40s 两条）
    /// - site1 TIMEOUT  @ now + 40_000  → 被覆盖
    /// - site1 TIMEOUT  @ now + 30_000  → 被覆盖
    /// - site2 DOWN     @ now           → 唯一保留
    /// - site3 TIMEOUT  @ now           → 被覆盖
    /// 期望 4 条，按 checkedAt DESC：
    ///   1) site3 TIMEOUT-3-latest（+60s）
    ///   2) site1 DOWN（+55s）
    ///   3) site1 TIMEOUT-3-latest（+50s）
    ///   4) site2 DOWN（now）
    @Test
    void recentIssues_dedupsBySiteAndStatusKeepingLatest() {
        var pageable = PageRequest.of(0, 20);
        List<SiteCheckHistory> issues = historyRepo.findRecentIssues(pageable);

        assertEquals(4, issues.size(), "duplicate (site, status) rows must collapse to one");

        // 验证每组只出现一次
        long site1Timeout = issues.stream()
                .filter(h -> h.getSiteId() == site1.getId() && h.getStatus() == CheckStatus.TIMEOUT)
                .count();
        long site1Down = issues.stream()
                .filter(h -> h.getSiteId() == site1.getId() && h.getStatus() == CheckStatus.DOWN)
                .count();
        long site3Timeout = issues.stream()
                .filter(h -> h.getSiteId() == site3.getId() && h.getStatus() == CheckStatus.TIMEOUT)
                .count();
        long site2Down = issues.stream()
                .filter(h -> h.getSiteId() == site2.getId() && h.getStatus() == CheckStatus.DOWN)
                .count();
        assertEquals(1, site1Timeout);
        assertEquals(1, site1Down);
        assertEquals(1, site3Timeout);
        assertEquals(1, site2Down);

        // 验证 site1 TIMEOUT 保留的是最新的（errorMessage = "timeout-3-latest"）
        var site1TimeoutRow = issues.stream()
                .filter(h -> h.getSiteId() == site1.getId() && h.getStatus() == CheckStatus.TIMEOUT)
                .findFirst().orElseThrow();
        assertEquals("timeout-3-latest", site1TimeoutRow.getErrorMessage());

        // 验证 site3 TIMEOUT 保留的是更新的（errorMessage = "timeout-latest"）
        var site3TimeoutRow = issues.stream()
                .filter(h -> h.getSiteId() == site3.getId() && h.getStatus() == CheckStatus.TIMEOUT)
                .findFirst().orElseThrow();
        assertEquals("timeout-latest", site3TimeoutRow.getErrorMessage());

        // 验证排序：最新一条在最前
        assertEquals(site3.getId(), issues.get(0).getSiteId(), "site3 TIMEOUT @ +60s 应排首位");
        assertEquals(CheckStatus.TIMEOUT, issues.get(0).getStatus());

        // 整列严格 checkedAt DESC
        for (int i = 1; i < issues.size(); i++) {
            assertTrue(issues.get(i - 1).getCheckedAt() >= issues.get(i).getCheckedAt(),
                    "issues must be ordered by checkedAt DESC at index " + i);
        }
    }
}