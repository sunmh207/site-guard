/// 子路由检测历史 DTO（前后端字段名约定）。
///
/// 镜像 SiteCheckHistoryDto，增加 ruleId / path / text_matched 以支撑路径维度展示。
///
/// 字段语义：
/// - ruleId：关联的路径规则 id
/// - path：探测路径（冗余自规则，规则删除后历史仍可读）
/// - checkedAt：探测发生的毫秒时间戳
/// - status：探测动作结果；UP=拿到响应，ERROR=探测本身失败（超时/连接失败等）
/// - httpStatus：HTTP 响应码；探测失败时为 null
/// - textMatched：KEYWORD 模式下是否命中关键字；其余模式为 null
/// - errorMessage：探测失败原因；成功时为 null；数据库字段限长 512
import type { CheckStatus } from '~/features/site/types/check-status'

export interface SitePathCheckHistoryDto {
  id: number
  siteId: number
  ruleId: number
  path: string
  checkedAt: number
  status: CheckStatus
  httpStatus: number | null
  textMatched: boolean | null
  errorMessage: string | null
}
