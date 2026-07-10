/// 连续失败阈值配置 dto。
///
/// 包一层 ConfigResponse 以兼容后端 StatusResult；ConfigResponse 是通用后端响应包装，
/// 已上移至 ~/shared/types/api，多 feature 共享。
import type { ConfigResponse } from '~/shared/types/api'

export interface AlertConfirmConfig {
  /// 连续失败 N 次后才触发告警；null 表示未设置（走后端默认值）。
  consecutiveFailuresBeforeAlert: number | null
}

export type AlertConfirmConfigResponse = ConfigResponse<AlertConfirmConfig>
