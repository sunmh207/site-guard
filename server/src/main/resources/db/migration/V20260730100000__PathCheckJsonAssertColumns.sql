ll-- 子路由健康检查：新增 JSON 条件配置和最近评估结果。
ALTER TABLE `site_path_rule`
    ADD COLUMN `assertion_config` TEXT NULL COMMENT
        'JSON 条件配置（versioned JSON）；check_type=JSON_ASSERT 时必填' AFTER `expected_text`,
    ADD COLUMN `last_json_matched` BOOLEAN NULL COMMENT
        '最近一次 JSON 条件是否满足；null=未探测/请求失败' AFTER `last_text_matched`,
    ADD COLUMN `last_json_detail` VARCHAR(2048) NULL COMMENT
        'JSON 解析或条件评估摘要，不保存完整响应体' AFTER `last_json_matched`;

ALTER TABLE `site_path_check_history`
    ADD COLUMN `json_matched` BOOLEAN NULL COMMENT
        'JSON 条件是否满足；仅 JSON_ASSERT 模式有效' AFTER `text_matched`,
    ADD COLUMN `json_detail` VARCHAR(2048) NULL COMMENT
        'JSON 解析或条件评估摘要，不保存完整响应体' AFTER `json_matched`;
