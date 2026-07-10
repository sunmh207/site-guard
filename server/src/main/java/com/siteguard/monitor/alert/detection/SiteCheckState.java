package com.siteguard.monitor.alert.detection;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/// 站点在某 AlertKind + bucket 下的状态机快照（边沿判断的依据）。
///
/// 字段语义：
/// - id           : 复合主键 (siteId, alertKind, bucket)
///                   PATH_CHECK 时 bucket = pathKey；其他 kind 为状态档位
/// - lastNotifiedAt : 上次发送通知的 epoch 毫秒；0 代表建库后尚未通知过
/// - updatedAt    : 本次评估时间
///
/// 该表只持久化"已通知状态"，与通知流水 (notification) 配合：
/// 当 AlertDetectionService 检测到 observed bucket 集合 ≠ 此处 bucket 集合时，
/// 写一条 notification + 增删这里的行，实现集合差触发的边沿事件。
@Entity
@Table(name = "site_check_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteCheckState {

    @EmbeddedId
    private SiteCheckStateId id;

    @Column(name = "last_notified_at", nullable = false)
    private long lastNotifiedAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
}
