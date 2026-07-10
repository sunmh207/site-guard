/// 通知设置表单的 valibot 校验规则。
///
/// 校验策略与后端 DTO 上的注解严格对齐：
///   - enabled：可选布尔
///   - platform：必填，枚举字符串
///   - webhookUrl：必填字符串，必须 https?:// 开头
///   - secret：可选字符串，最长 255 字符
import * as v from 'valibot'

const PLATFORM_VALUES = ['DINGTALK', 'WECHAT_WORK', 'FEISHU'] as const

export const notificationConfigSchema = v.object({
  enabled: v.optional(v.boolean()),
  platform: v.pipe(
    v.string('平台必须是字符串'),
    v.picklist(PLATFORM_VALUES, '请选择平台'),
  ),
  webhookUrl: v.pipe(
    v.string('Webhook URL 必须是字符串'),
    v.minLength(1, '请输入 Webhook URL'),
    v.maxLength(1024, 'Webhook URL 最多 1024 字符'),
    v.regex(/^https?:\/\/.+/, 'Webhook URL 必须以 http:// 或 https:// 开头'),
  ),
  secret: v.optional(
    v.pipe(
      v.string('签名密钥必须是字符串'),
      v.maxLength(255, '签名密钥最多 255 字符'),
    ),
  ),
})

export type NotificationConfigForm = v.InferOutput<typeof notificationConfigSchema>