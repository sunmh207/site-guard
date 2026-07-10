-- 通用 KV 配置表（替代 im_robot 单用途表）

CREATE TABLE `system_config` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `config_key`    VARCHAR(64)  NOT NULL COMMENT '配置键',
    `config_value`  TEXT         NOT NULL COMMENT '配置值（JSON 序列化）',
    `created_at`    BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `updated_at`    BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    UNIQUE KEY `uk_system_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';