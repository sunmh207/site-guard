/// 通知设置模块的类型定义。
///
/// 后端：系统配置（key=notification）的 JSON 值结构，
///       与 java NotificationConfig 一一对应。

export type RobotPlatform = 'DINGTALK' | 'WECHAT_WORK' | 'FEISHU'

export interface NotificationConfig {
  /// 是否启用
  enabled?: boolean
  /// 平台
  platform: RobotPlatform
  /// Webhook URL
  webhookUrl: string
  /// 签名密钥（可选）
  secret?: string
}

export interface ConfigResponse<T = unknown> {
  key: string
  value: T
  updatedAt: number
}

export interface TestWebhookParams {
  platform: RobotPlatform
  webhookUrl: string
  secret?: string
}

export interface TestWebhookResult {
  success: boolean
  message: string
}