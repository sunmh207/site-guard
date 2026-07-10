-- 边沿触发告警系统的状态机快照表。
--
-- site_check_state : 每个 (site, alert_kind, bucket) 唯一一行，保存"上次已通知"的状态机 bucket；
--                   AlertDetectionService 用它做边沿判断（newBucket != oldBucket 才发）。
--
-- 命名：与既有 site_check_history 保持前缀一致。
--
-- 主键选择 (site_id, alert_kind, bucket)：
--   PATH_CHECK 一个 site 可能有多个 bucket（每个失败路径一个 bucket），
--   所以 PK 必须包含 bucket 才能在 INSERT 时 ON DUPLICATE KEY UPDATE。
--   见 PathCheckDecomposition 设计文档。

CREATE TABLE `site_check_state` (
    `site_id`            BIGINT      NOT NULL                COMMENT 'site.id',
    `alert_kind`         VARCHAR(32) NOT NULL                COMMENT 'AlertKind 枚举名：AVAILABILITY / CERT_EXPIRY / DOMAIN_EXPIRING / PATH_CHECK / ...',
    `bucket`             VARCHAR(32) NOT NULL                COMMENT '状态机 key：UP/DOWN/NORMAL/W14/W7/W3/EXPIRED/<path>...',
    `last_notified_at`   BIGINT      NOT NULL                COMMENT '上次发送通知的 epoch 毫秒；0 代表建库后尚未通知过',
    `updated_at`         BIGINT      NOT NULL                COMMENT '本次评估时间（epoch 毫秒）',
    PRIMARY KEY (`site_id`, `alert_kind`, `bucket`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='告警状态机快照（边沿判断用）';