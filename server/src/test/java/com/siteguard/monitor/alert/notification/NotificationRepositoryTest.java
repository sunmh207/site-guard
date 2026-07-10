package com.siteguard.monitor.alert.notification;

import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// NotificationRepository 集成测试：覆盖 save + 按 sentAt DESC 取最近 N 条。
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:test-db/migration",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.url=jdbc:h2:mem:notif-repo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class NotificationRepositoryTest {

    @Autowired
    NotificationRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
    }

    @Test
    void savePersistsAllFields() {
        var n = Notification.builder()
                .siteId(1L)
                .alertKind(AlertKind.AVAILABILITY)
                .status(AlertStatus.ABNORMAL)
                .bucket("DOWN")
                .message("服务不可用")
                .sentAt(1_000L)
                .deliveryStatus(NotificationDeliveryStatus.SUCCESS)
                .errorMessage(null)
                .retryCount(0)
                .build();
        var saved = repo.save(n);

        assertNotNull(saved.getId());
        var found = repo.findById(saved.getId()).orElseThrow();
        assertEquals(1L, found.getSiteId());
        assertEquals(AlertKind.AVAILABILITY, found.getAlertKind());
        assertEquals(AlertStatus.ABNORMAL, found.getStatus());
        assertEquals("DOWN", found.getBucket());
        assertEquals("服务不可用", found.getMessage());
        assertEquals(NotificationDeliveryStatus.SUCCESS, found.getDeliveryStatus());
    }

    @Test
    void defaultDeliveryStatus_isPending() {
        var n = Notification.builder()
                .siteId(1L)
                .alertKind(AlertKind.CERT_EXPIRY)
                .status(AlertStatus.ABNORMAL)
                .bucket("W7")
                .message("证书将于 5 天后过期")
                .sentAt(1_000L)
                .build();
        var saved = repo.save(n);

        assertEquals(NotificationDeliveryStatus.PENDING, saved.getDeliveryStatus());
    }

    @Test
    void findAllBySentAtDesc_ordersLatestFirst() {
        repo.save(notif(1L, "W14", 100L));
        repo.save(notif(1L, "W7",  300L));
        repo.save(notif(2L, "DOWN", 200L));

        Pageable page = PageRequest.of(0, 10);
        List<Notification> all = repo.findAllByOrderBySentAtDesc(page);

        assertEquals(3, all.size());
        // 倒序：300, 200, 100
        assertEquals(300L, all.get(0).getSentAt());
        assertEquals(200L, all.get(1).getSentAt());
        assertEquals(100L, all.get(2).getSentAt());
    }

    @Test
    void pageableLimitsResultSize() {
        for (int i = 0; i < 5; i++) {
            repo.save(notif(1L, "DOWN", i));
        }

        List<Notification> top2 = repo.findAllByOrderBySentAtDesc(PageRequest.of(0, 2));
        assertEquals(2, top2.size());
        // 5, 4 两条
        assertEquals(4L, top2.get(0).getSentAt());
        assertEquals(3L, top2.get(1).getSentAt());
    }

    @Test
    void emptyRepo_returnsEmptyList() {
        assertTrue(repo.findAllByOrderBySentAtDesc(PageRequest.of(0, 10)).isEmpty());
    }

    private Notification notif(long siteId, String bucket, long sentAt) {
        return Notification.builder()
                .siteId(siteId)
                .alertKind(AlertKind.CERT_EXPIRY)
                .status(AlertStatus.ABNORMAL)
                .bucket(bucket)
                .message("m-" + sentAt)
                .sentAt(sentAt)
                .deliveryStatus(NotificationDeliveryStatus.SUCCESS)
                .retryCount(0)
                .build();
    }
}
