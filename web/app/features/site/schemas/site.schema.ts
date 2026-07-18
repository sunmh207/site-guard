/// Site 模块表单校验规则（valibot）。
///
/// 校验策略：
/// - name：必填字符串，1-128 字符
/// - url：必填字符串，1-512 字符，必须以 http:// 或 https:// 开头
/// - consecutiveFailuresBeforeAlert：可选整数 ≥1；null/缺失 = 走全局默认
///
/// 与后端 SiteCreateParams 上的 @Pattern / @Length 保持严格一致。
import * as v from 'valibot'

/// 连续失败阈值：允许缺失（undefined）/显式 null，或者填写整数 ≥ 1。
/// 在 valibot 1.x 中：v.nullish 把值层变成"可空或可缺"，v.optional 再让对象 key 可省略。
const consecutiveFailuresBeforeAlertSchema = v.nullish(
  v.pipe(v.number('连续失败阈值必须是数字'), v.minValue(1, '连续失败阈值必须 ≥ 1')),
)

export const siteCreateSchema = v.object({
  /// 站点名称
  name: v.pipe(
    v.string('站点名称必须是字符串'),
    v.minLength(1, '请输入站点名称'),
    v.maxLength(128, '站点名称最多 128 个字符'),
  ),
  /// 站点 URL
  url: v.pipe(
    v.string('URL 必须是字符串'),
    v.minLength(1, '请输入 URL'),
    v.maxLength(512, 'URL 最多 512 个字符'),
    v.regex(/^https?:\/\/.+/, 'URL 必须以 http:// 或 https:// 开头'),
  ),
  /// 站点级连续失败阈值覆盖（null/缺失 = 走全局默认）
  consecutiveFailuresBeforeAlert: v.optional(consecutiveFailuresBeforeAlertSchema),
  /// 证书校验分级放行开关（缺失 = 走全局默认 false）
  certForgiveChainIncomplete: v.optional(v.boolean()),
  certForgiveDomainMismatch: v.optional(v.boolean()),
  certForgiveSelfSigned: v.optional(v.boolean()),
})

/// 由 schema 推导的表单数据类型
export type SiteCreateForm = v.InferOutput<typeof siteCreateSchema>