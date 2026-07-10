CREATE TABLE category (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id     BIGINT       NULL,
    name          VARCHAR(64)  NOT NULL,
    seq           INT          NOT NULL DEFAULT 0,
    system_flag   TINYINT(1)   NOT NULL DEFAULT 0,
    created_at    BIGINT       NOT NULL,
    updated_at    BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_parent_name (parent_id, name),
    KEY idx_category_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点分类表';

INSERT INTO category (parent_id, name, seq, system_flag, created_at, updated_at)
VALUES (NULL, '默认分类', 0, 1, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);