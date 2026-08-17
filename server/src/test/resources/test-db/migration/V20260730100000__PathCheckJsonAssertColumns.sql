-- H2 测试库：子路由 JSON 条件检测字段。
ALTER TABLE site_path_rule
    ADD COLUMN assertion_config TEXT NULL;

ALTER TABLE site_path_rule
    ADD COLUMN last_json_matched BOOLEAN NULL;

ALTER TABLE site_path_rule
    ADD COLUMN last_json_detail VARCHAR(2048) NULL;

ALTER TABLE site_path_check_history
    ADD COLUMN json_matched BOOLEAN NULL;

ALTER TABLE site_path_check_history
    ADD COLUMN json_detail VARCHAR(2048) NULL;
