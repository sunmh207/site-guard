-- 子路由检测历史表
--
-- 镜像 site_check_history 的设计：每次 PathCheckProbe 探测一条路径规则后写一行。
-- 用途：故障排查时能看到某条路径的历史状态变化，而不是只有"最近一次"快照。
--
-- 与 site_path_rule 没有外键（项目惯例：cascade 在 service 层维护）。
-- 站点删除 / 规则删除时由 SitePathHistoryCleanupJob 按 site_id 清理，
-- 历史数据整体保留 7 天（与 site_check_history 一致）。
CREATE TABLE `site_path_check_history`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `site_id`       bigint       NOT NULL                COMMENT '逻辑关联 site.id',
    `rule_id`       bigint       NOT NULL                COMMENT '逻辑关联 site_path_rule.id（规则删除后历史仍保留，便于排查）',
    `path`          varchar(512) NOT NULL                COMMENT '探测路径（冗余自 rule.path，规则删除后历史仍可读）',
    `checked_at`    bigint       NOT NULL                COMMENT '检测时间戳（毫秒）',
    `status`        varchar(16)  NOT NULL                COMMENT '检测结果：UP / DOWN / TIMEOUT / ERROR（与 CheckStatus 枚举对齐）',
    `http_status`   int                   DEFAULT NULL  COMMENT 'HTTP 状态码（如 200/404/500）；连接失败/超时时为 null',
    `text_matched`  tinyint(1)            DEFAULT NULL  COMMENT '关键字是否命中；仅 KEYWORD 模式有效，其余为 null；探测失败也为 null',
    `error_message` varchar(512)          DEFAULT NULL  COMMENT '检测失败时的错误信息；成功时为 null',
    PRIMARY KEY (`id`),
    -- 按规则拉取历史（slideover 展示某条路径的历史）
    KEY `idx_rule_checked` (`rule_id`, `checked_at`),
    -- 按站点清理（站点删除时）
    KEY `idx_site_checked` (`site_id`, `checked_at`),
    -- 清理任务按时间删除过期历史
    KEY `idx_path_hist_checked` (`checked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='子路由检测历史表';
