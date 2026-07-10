/// 连续失败阈值配置 dto。
///
/// 与 notification-setting.dto.ts 同款结构：包一层 ConfigResponse 以兼容后端 StatusResult。
import type { ConfigResponse } from '~/features/notification-setting/types/notification-setting.dto'

export interface AlertConfirmConfig {
  /// 连续失败 N 次后才触发告警；null 表示未设置（走后端默认值）。
  consecutiveFailuresBeforeAlert: number | null
}

export type AlertConfirmConfigResponse = ConfigResponse<AlertConfirmConfig>
