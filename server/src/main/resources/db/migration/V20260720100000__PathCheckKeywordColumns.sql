-- 子路由健康检查：新增「包含关键字」判定标准
--
-- check_type      : 判定类型，默认 'HTTP_STATUS'（存量行不变）
-- expected_text   : 关键字文本，KEYWORD 模式必填
-- last_text_matched: 最近一次是否命中关键字，NULL=未探测/探测失败
--
-- expected_http_status 列保持 NOT NULL 不改：KEYWORD 规则也填占位值（如 200），
-- 但 isFailing 在 KEYWORD 模式下忽略它，避免改列约束影响存量。

-- check_type 存放枚举 name()（大写 HTTP_STATUS / KEYWORD），与 @Enumerated(STRING) 一致。
-- 存量行默认 HTTP_STATUS，isFailing 对存量走原有状态码分支，行为完全不变。

ALTER TABLE `site_path_rule`
    ADD COLUMN `check_type` VARCHAR(16) NOT NULL DEFAULT 'HTTP_STATUS' COMMENT
        '判定类型：HTTP_STATUS / KEYWORD（枚举 name，大写）；默认 HTTP_STATUS 兼容存量' AFTER `path`,
    ADD COLUMN `expected_text` VARCHAR(255) NULL COMMENT
        '关键字；check_type=KEYWORD 时必填' AFTER `check_type`,
    ADD COLUMN `last_text_matched` BOOLEAN NULL COMMENT
        '最近一次是否命中关键字；null=未探测/探测失败' AFTER `last_error_message`;
