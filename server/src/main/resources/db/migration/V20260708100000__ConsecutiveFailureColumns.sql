-- 连续失败阈值告警相关字段
-- 用于"连续失败阈值告警"特性：站点级可用性（availability）与子路由（path rule）
-- 两类告警均改为累计连续失败次数达阈值后才触发，避免单次抖动误报。
--
-- consecutive_failures_before_alert   : site 级别阈值覆盖；NULL 表示沿用全局配置默认（1）
-- consecutive_availability_failures  : availability 维度连续失败计数，probe 层维护，UP 探测归零
-- consecutive_failures (site_path_rule): path rule 维度连续失败计数，probe 层维护，探测成功归零
-- 上述计数器均为 INT，足以承载实际场景（一年内失败次数远小于 INT 上限）。

ALTER TABLE `site`
    ADD COLUMN `consecutive_failures_before_alert` INT NULL
        COMMENT '站点级连续失败阈值覆盖；null 表示沿用全局配置默认（1）' after `availability_status`,
    ADD COLUMN `consecutive_availability_failures` INT NOT NULL DEFAULT 0
        COMMENT '连续 DOWN 探测次数；probe 层维护，UP 探测归零；int 足以承载实际场景' after `consecutive_failures_before_alert`;

ALTER TABLE `site_path_rule`
    ADD COLUMN `consecutive_failures` INT NOT NULL DEFAULT 0
        COMMENT '连续探测失败次数；probe 层维护，探测成功归零' after `expected_http_status`;