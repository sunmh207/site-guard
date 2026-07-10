/// 站点探测历史 DTO（前后端字段名约定）。
///
/// 字段语义：
/// - checkedAt：探测发生的毫秒时间戳（不是"入库时间"），由 probe 落库时写入
/// - status：4 态枚举；与后端 com.siteguard.monitor.entity.CheckStatus 字符串对齐
/// - httpStatus：HTTP 响应码；TIMEOUT/ERROR 时为 null
/// - responseMs：探测总耗时（毫秒）；TIMEOUT 时为探测超时上限（参见 HttpSiteProbe）
/// - errorMessage：探测失败原因；成功时为 null；数据库字段限长 512
import type { CheckStatus } from './check-status'

export interface SiteCheckHistoryDto {
  id: number
  siteId: number
  checkedAt: number
  status: CheckStatus
  httpStatus: number | null
  responseMs: number | null
  errorMessage: string | null
}