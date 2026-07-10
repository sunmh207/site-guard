package com.siteguard.monitor.alert.detection;

import com.siteguard.monitor.alert.AlertKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// SiteCheckStateRepository 集成测试：覆盖 (siteId, alertKind) 复合主键的 upsert 与按 kind 过滤。
///
/// H2 + Flyway + ddl-auto=validate 校验 Entity 与迁移对齐。
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:test-db/migration",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.url=jdbc:h2:mem:state-repo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class SiteCheckStateRepositoryTest {

    @Autowired
    SiteCheckStateRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
    }

    @Test
    void saveAndLoadByCompositeKey() {
        var state = SiteCheckState.builder()
                .id(new SiteCheckStateId(1L, "AVAILABILITY", "DOWN"))
                .lastNotifiedAt(1000L)
                .updatedAt(2000L)
                .build();
        repo.save(state);

        Optional<SiteCheckState> loaded = repo.findById(new SiteCheckStateId(1L, "AVAILABILITY", "DOWN"));
        assertTrue(loaded.isPresent());
        assertEquals("DOWN", loaded.get().getId().bucket());
        assertEquals(1000L, loaded.get().getLastNotifiedAt());
    }

    @Test
    void multipleBucketsForSameSite_keepDistinctRows() {
        // (siteId, alertKind, bucket) 是复合 PK：不同 bucket 视为不同状态机行
        // 实际场景：cert 从 W14 升档到 W7 时 AlertDetectionService 通过集合差算法
        // DELETE 旧行 + INSERT 新行，不是 UPDATE。这里验证两条 (W14, W7) 行都能写入。
        var w14 = SiteCheckState.builder()
                .id(new SiteCheckStateId(1L, "CERT_EXPIRY", "W14"))
                .lastNotifiedAt(0L)
                .updatedAt(100L)
                .build();
        var w7 = SiteCheckState.builder()
                .id(new SiteCheckStateId(1L, "CERT_EXPIRY", "W7"))
                .lastNotifiedAt(500L)
                .updatedAt(600L)
                .build();
        repo.save(w14);
        repo.save(w7);

        var all = repo.findAll();
        assertEquals(2, all.size(), "不同 bucket 是不同行");
        assertEquals(500L, w7.getLastNotifiedAt());
    }

    @Test
    void findByAlertKind_filtersAndOrdersBySiteId() {
        // (site=1, AVAILABILITY) + (site=1, CERT_EXPIRY) + (site=2, CERT_EXPIRY)
        repo.save(stateOf(1L, "AVAILABILITY", "UP"));
        repo.save(stateOf(1L, "CERT_EXPIRY",  "W7"));
        repo.save(stateOf(2L, "CERT_EXPIRY",  "EXPIRED"));

        List<SiteCheckState> certs = repo.findByAlertKind(AlertKind.CERT_EXPIRY);

        assertEquals(2, certs.size());
        // 升序：siteId 1 在前
        assertEquals(1L, certs.get(0).getId().siteId());
        assertEquals(2L, certs.get(1).getId().siteId());
        assertEquals("W7",     certs.get(0).getId().bucket());
        assertEquals("EXPIRED", certs.get(1).getId().bucket());
    }

    @Test
    void findByAlertKind_emptyWhenNoMatch() {
        repo.save(stateOf(1L, "AVAILABILITY", "UP"));
        assertNotNull(repo.findByAlertKind(AlertKind.CERT_EXPIRY));
        assertTrue(repo.findByAlertKind(AlertKind.CERT_EXPIRY).isEmpty());
    }

    @Test
    void deleteBySiteIdAndAlertKindAndBucketIn_removesOnlyMatchingRows() {
        repo.save(stateOf(1L, "PATH_CHECK", "/api/a"));
        repo.save(stateOf(1L, "PATH_CHECK", "/api/b"));
        repo.save(stateOf(1L, "PATH_CHECK", "/api/c"));
        repo.save(stateOf(2L, "PATH_CHECK", "/api/a"));

        int deleted = repo.deleteBySiteIdAndAlertKindAndBucketIn(
                1L, AlertKind.PATH_CHECK, Set.of("/api/a", "/api/b"));
        assertEquals(2, deleted);

        List<SiteCheckState> remaining = repo.findByAlertKind(AlertKind.PATH_CHECK);
        assertEquals(2, remaining.size());
        assertTrue(remaining.stream().anyMatch(s ->
                s.getId().siteId() == 1L && "/api/c".equals(s.getId().bucket())));
        assertTrue(remaining.stream().anyMatch(s ->
                s.getId().siteId() == 2L && "/api/a".equals(s.getId().bucket())));
    }

    @Test
    void deleteBySiteIdAndAlertKindAndBucketIn_emptyBuckets_deletesNothing() {
        repo.save(stateOf(1L, "PATH_CHECK", "/api/a"));
        int deleted = repo.deleteBySiteIdAndAlertKindAndBucketIn(
                1L, AlertKind.PATH_CHECK, Set.of());
        assertEquals(0, deleted);
        assertEquals(1, repo.findByAlertKind(AlertKind.PATH_CHECK).size());
    }

    private SiteCheckState stateOf(long siteId, String kind, String bucket) {
        return SiteCheckState.builder()
                .id(new SiteCheckStateId(siteId, kind, bucket))
                .lastNotifiedAt(0L)
                .updatedAt(0L)
                .build();
    }
}
