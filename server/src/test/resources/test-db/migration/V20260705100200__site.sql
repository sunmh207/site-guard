CREATE TABLE `site`
(
    `id`                     bigint       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `name`                   varchar(128) NOT NULL COMMENT '站点名称',
    `url`                    varchar(512) NOT NULL COMMENT '站点 URL（必须以 http:// 或 https:// 开头）',
    `category_id`            bigint       NOT NULL                COMMENT '所属分类 id（无外键，service 层校验）',
    `availability_status`    varchar(16)           DEFAULT NULL COMMENT '可用性状态：UNKNOWN / UP / DOWN',
    `last_checked_at`        bigint                DEFAULT NULL COMMENT '上次检测时间戳（毫秒）',
    `certificate_expires_at` bigint                DEFAULT NULL COMMENT '证书到期时间戳（毫秒）',
    `domain_expires_at`      bigint                DEFAULT NULL COMMENT '域名到期时间戳（毫秒）',
    `certificate_issuer`     varchar(256)          DEFAULT NULL COMMENT '证书签发机构',
    `paused`                 tinyint(1)   NOT NULL DEFAULT 0      COMMENT '是否暂停监控（true=不参与扫描）',
    `created_at`             bigint       NOT NULL,
    `updated_at`             bigint       NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_site_name` (`name`),
    UNIQUE KEY `uk_site_url`  (`url`),
    KEY `idx_site_category`   (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='被监控站点表';