-- 站点自定义子路由检测规则
-- 每个 rule 在 SiteCheckJob 中执行一次 HTTP GET，
-- 把结果写回 last_* 字段，供 PathCheckAlertDefinition 在 AlertDetectionJob 中读取做边沿判断。
CREATE TABLE site_path_rule (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  site_id         BIGINT       NOT NULL                COMMENT '所属站点 id（无外键，service 层联动删除）',
  path            VARCHAR(512) NOT NULL                COMMENT '相对路径，如 /app_dev.php、/systeminfo；拼接在 site.url 之后',
  expected_http_status INT    NOT NULL                COMMENT '期望 HTTP 状态码，如 200 / 404',

  -- 探测状态字段（PathCheckProbe 写入，PathCheckAlertDefinition 读取）
  last_checked_at     BIGINT   NULL                    COMMENT '最近一次探测时间戳（毫秒）',
  last_http_status    INT      NULL                    COMMENT '最近一次实际 HTTP 状态码；连接失败/超时时为 null',
  last_error_message  VARCHAR(512) NULL                 COMMENT '最近一次探测的可读错误摘要；成功时为 null',

  created_at  BIGINT NOT NULL                          COMMENT '创建时间戳（毫秒）',
  updated_at  BIGINT NOT NULL                          COMMENT '更新时间戳（毫秒）',

  PRIMARY KEY (id),
  KEY idx_site_path_rule_site (site_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站点自定义子路由检测规则';