package com.siteguard.system.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// 定时发送报告配置（存储于 system_config.config_value，JSON 序列化）。
///
/// 字段语义：
///   - enabled: 是否启用定时报告；null/false → 调度器跳过
///   - time: 每日发送时刻，"HH:mm"（24 小时制）；调度器用 LocalTime.parse 解析，
///     格式非法时 warn 跳过、不崩溃
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledReportConfig {

    private Boolean enabled;

    private String time;
}
