CREATE TABLE `notification` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `site_id`           BIGINT        NOT NULL                COMMENT '逻辑关联 site.id；不建外键，由 SiteServiceImpl 同步删除',
    `alert_kind`        VARCHAR(32)   NOT NULL                COMMENT 'AlertKind 枚举名',
    `status`            VARCHAR(16)   NOT NULL                COMMENT 'AlertStatus 枚举名：NORMAL=恢复事件 / ABNORMAL=新异常事件',
    `bucket`            VARCHAR(32)   NOT NULL                COMMENT '触发本次通知的状态机 bucket',
    `message`           VARCHAR(1024) NOT NULL                COMMENT '已发送给 IM 渠道的人读文本',
    `sent_at`           BIGINT        NOT NULL                COMMENT '本条记录写入时间（epoch 毫秒）',
    `delivery_status`   VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'NotificationDeliveryStatus 枚举名',
    `error_message`     VARCHAR(1024) DEFAULT NULL             COMMENT '发送失败时的错误摘要（截断 1024 字符）',
    `retry_count`       INT           NOT NULL DEFAULT 0      COMMENT '重试次数；P1 阶段固定为 0',
    KEY `idx_notification_site_sent` (`site_id`, `sent_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='通知发送流水（边沿事件日志）';