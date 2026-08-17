-- H2 测试库：补齐生产库已有的子路由关键字检测字段。
ALTER TABLE site_path_rule
    ADD COLUMN check_type VARCHAR(16) NOT NULL DEFAULT 'HTTP_STATUS';

ALTER TABLE site_path_rule
    ADD COLUMN expected_text VARCHAR(255) NULL;

ALTER TABLE site_path_rule
    ADD COLUMN last_text_matched BOOLEAN NULL;
