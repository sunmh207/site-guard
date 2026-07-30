/// 子路由检测模式。
export type PathCheckType = 'HTTP_STATUS' | 'KEYWORD' | 'JSON_ASSERT'

export type JsonConditionCombinator = 'ALL' | 'ANY'

export type JsonConditionOperator
  = | 'IS_TRUE'
    | 'IS_FALSE'
    | 'NUMBER_EQ'
    | 'NUMBER_NE'
    | 'NUMBER_GT'
    | 'NUMBER_GTE'
    | 'NUMBER_LT'
    | 'NUMBER_LTE'
    | 'STRING_EQ'
    | 'STRING_NE'
    | 'STRING_CONTAINS'
    | 'STRING_NOT_CONTAINS'
    | 'EXISTS'
    | 'NOT_EXISTS'
    | 'IS_NULL'
    | 'IS_NOT_NULL'

export interface JsonConditionDto {
  path: string
  operator: JsonConditionOperator
  expectedValue: string | null
}

export interface JsonAssertionConfigDto {
  version: 1
  combinator: JsonConditionCombinator
  conditions: JsonConditionDto[]
}

/// 站点自定义子路由检测规则 DTO（前后端共享字段名约定）。
export interface SitePathRuleDto {
  id: number | null
  siteId: number
  path: string
  expectedHttpStatus: number
  checkType: PathCheckType
  expectedText: string | null
  assertionConfig: JsonAssertionConfigDto | null
  lastCheckedAt: number | null
  lastHttpStatus: number | null
  lastTextMatched: boolean | null
  lastJsonMatched: boolean | null
  lastJsonDetail: string | null
  lastErrorMessage: string | null
  alertingSince: number | null
}

export interface SitePathRuleListRequest {
  siteId: number
  rules: SitePathRuleDto[]
}

export interface SitePathRuleTestRequest {
  path: string
  expectedHttpStatus: number
  checkType: PathCheckType
  expectedText: string | null
  assertionConfig: JsonAssertionConfigDto | null
}

export type JsonActualType = 'MISSING' | 'NULL' | 'BOOLEAN' | 'NUMBER' | 'STRING' | 'ARRAY' | 'OBJECT' | 'INVALID_JSON'

export interface JsonConditionDiagnosticDto {
  index: number
  path: string
  operator: JsonConditionOperator
  matched: boolean
  actualType: JsonActualType
  actualValue: string | null
  expectedValue: string | null
  reason: string
}

export interface SitePathRuleTestResultDto {
  requestCompleted: boolean
  httpStatus: number | null
  httpStatusMatched: boolean
  bodyParsed: boolean | null
  jsonMatched: boolean | null
  textMatched: boolean | null
  healthy: boolean
  summary: string
  conditions: JsonConditionDiagnosticDto[]
  errorMessage: string | null
}
