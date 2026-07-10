package com.siteguard.monitor.alert;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/// 仪表盘统一告警条目。
///
/// 字段扁平，不为每种 kind 暴露结构化数据：人读语义全部由 source 写入 [message]，
/// 前端只展示 message 与按 kind 选择图标/颜色。便于未来"路径探测/反向探测"等
/// 同样以文本为载体的告警零成本接入。
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AlertDTO implements Serializable {
    private Long siteId;
    private String siteName;
    private String siteUrl;
    private AlertKind kind;
    private AlertStatus status;

    /// 观察时间戳（毫秒）：
    /// - AVAILABILITY    : probe checkedAt
    /// - CERT/DOMAIN_EXPIRING : site.lastCheckedAt（若 null 则退化为聚合当时的 now）
    private Long detectedAt;

    /// 人读告警文本，例如
    /// - "HTTP 500: 连接被拒绝"
    /// - "请求超时 (5000ms)"
    /// - "证书将于 7 天后过期"
    /// - "证书已过期 3 天"
    /// - "路径 /admin/debug 应禁用但当前返回 200"
    private String message;
}