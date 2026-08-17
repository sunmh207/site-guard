CREATE TABLE category (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id     BIGINT       NULL,
    name          VARCHAR(64)  NOT NULL,
    seq           INT          NOT NULL DEFAULT 0,
    system_flag   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    BIGINT       NOT NULL,
    updated_at    BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_category_parent_name UNIQUE (parent_id, name)
);

CREATE INDEX idx_category_parent ON category(parent_id);

INSERT INTO category (parent_id, name, seq, system_flag, created_at, updated_at)
VALUES (NULL, '默认分类', 0, TRUE, 0, 0);
