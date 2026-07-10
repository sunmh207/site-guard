CREATE TABLE `site_check_history`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `site_id`       bigint       NOT NULL                COMMENT '逻辑关联 site.id；不建外键，避免站点删除时历史写入冲突（由 SiteServiceImpl 同步清理）',
    `checked_at`    bigint       NOT NULL                COMMENT '检测时间戳（毫秒）',
    `status`        varchar(16)  NOT NULL                COMMENT '检测结果：UP / DOWN（应用层用 CheckStatus 枚举约束，DB 层不再加 CHECK 以保持与 site 表一致）',
    `http_status`   int                   DEFAULT NULL  COMMENT 'HTTP 状态码（如 200/404/500）',
    `response_ms`   int                   DEFAULT NULL  COMMENT '响应耗时（毫秒）',
    `error_message` varchar(512)          DEFAULT NULL  COMMENT '检测失败时的错误信息',
    PRIMARY KEY (`id`),
    -- 用于按站点拉取一段时间内的检测历史（站点详情页时间线）
    KEY `idx_site_checked` (`site_id`, `checked_at`),
    -- 用于仪表盘按时间窗查询指定状态的记录（全局异常聚合）
    KEY `idx_checked_status` (`checked_at`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站点检测历史表';