/// 判定类型：HTTP_STATUS（默认）/ KEYWORD
export type PathCheckType = 'HTTP_STATUS' | 'KEYWORD'

/// 站点自定义子路由检测规则 DTO（前后端共享字段名约定）
export interface SitePathRuleDto {
  id: number | null
  siteId: number
  path: string
  expectedHttpStatus: number
  /// 判定类型：HTTP_STATUS（默认）/ KEYWORD
  checkType: PathCheckType
  /// 关键字；checkType=KEYWORD 时必填
  expectedText: string | null
  lastCheckedAt: number | null
  lastHttpStatus: number | null
  /// 最近一次是否命中关键字；null = 未探测/探测失败
  lastTextMatched: boolean | null
  lastErrorMessage: string | null
  /// 当前是否在告警（site_check_state 存在 (site, PATH_CHECK, path) 行）。
  /// 未探测或恢复后为 null。
  alertingSince: number | null
}

/// 整批 set 请求体
export interface SitePathRuleListRequest {
  siteId: number
  rules: SitePathRuleDto[]
}
