CREATE TABLE site_check_state (
    site_id BIGINT NOT NULL,
    alert_kind VARCHAR(32) NOT NULL,
    bucket VARCHAR(32) NOT NULL,
    last_notified_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (site_id, alert_kind, bucket)
);
