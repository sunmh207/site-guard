/// 连续失败阈值表单 schema。
///
/// 校验规则与后端 ConsecutiveFailureConfig 一致：
///   - 必须是整数
///   - 最小值为 1（0 或负数无业务意义）
import * as v from 'valibot'

export const alertConfirmSettingSchema = v.object({
  consecutiveFailuresBeforeAlert: v.pipe(
    v.number('请输入数字'),
    v.minValue(1, '最小值为 1'),
    v.integer('必须是整数'),
  ),
})

export type AlertConfirmSettingForm = v.InferOutput<typeof alertConfirmSettingSchema>
