package com.siteguard.monitor.alert.notification;

import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/// 通知发送流水：每次边沿跳变都记一行。
///
/// 字段语义：
/// - status = NORMAL    ⇒ 一次"恢复"事件（任意异常 bucket → NORMAL bucket）
/// - status = ABNORMAL  ⇒ 一次"新异常"事件（NORMAL → 异常 bucket）
/// - delivery_status 默认 PENDING（落库即写入），由 NotificationListener 发送后更新 SUCCESS/FAILED
///
/// 不复用 BaseEntity：审计字段（id/sentAt）已覆盖业务字段；
/// Notification 是事件流，外部触发量大，独立存储更便于清理与归档。
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id", nullable = false)
    private Long siteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_kind", nullable = false, length = 32)
    private AlertKind alertKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertStatus status;

    @Column(nullable = false, length = 32)
    private String bucket;

    @Column(nullable = false, length = 1024)
    private String message;

    @Column(name = "sent_at", nullable = false)
    private long sentAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 16)
    private NotificationDeliveryStatus deliveryStatus = NotificationDeliveryStatus.PENDING;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;
}
