package com.siteguard.system.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// 连续失败阈值配置（存于 system_config.config_value，JSON 序列化）。
///
/// consecutiveFailuresBeforeAlert : 连续 N 次失败才触发告警
/// 默认 1：保持与改动前完全一致的行为
///
/// 用户可在 /api/v1/admin/config 上修改此配置，无需重启即生效
/// （AvailabilityAlertDefinition / PathCheckAlertDefinition 通过 ConfigService 每次 eval 实时读取）。
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsecutiveFailureConfig {

    private Integer consecutiveFailuresBeforeAlert;

    /// 默认阈值；项目启动且 system_config 中无对应配置时使用
    public static int defaultValue() {
        return 1;
    }

    /// 反序列化时若字段缺失/null，使用默认值；避免 NPE
    public int getConsecutiveFailuresBeforeAlertOrDefault() {
        return consecutiveFailuresBeforeAlert == null
                ? defaultValue()
                : consecutiveFailuresBeforeAlert;
    }
}
