package com.siteguard.system.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// 证书告警阈值配置（存储于 system_config.config_value，JSON 序列化）。
///
/// warningDays : 证书剩余天数 < N 时触发 W{N} 档告警
/// 默认 [14, 7, 3]：14 天初次预警，7 天加重，3 天紧急；超过 14 天不告警
///
/// 用户可在 /api/v1/admin/config 上修改此配置，无需重启即生效
/// （CertExpiryAlertDefinition 通过 Supplier 注入 ConfigService，每次检测重新读取）。
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertAlertConfig {

    private int[] warningDays;

    /// 默认阈值；项目启动且 system_config 中无 cert_alert 配置时使用
    public static int[] defaultWarningDays() {
        return new int[]{14, 7, 3};
    }

    /// JSON 反序列化时若 warningDays 缺失/为空，使用默认值；
    /// 避免 null 数组合后续代码 NPE
    public int[] getWarningDaysOrDefault() {
        return (warningDays == null || warningDays.length == 0)
                ? defaultWarningDays()
                : warningDays;
    }
}