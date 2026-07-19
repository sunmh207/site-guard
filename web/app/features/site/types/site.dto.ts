/**
 * Site 模块 - 后端接口契约 (DTO)
 *
 * 这份类型声明是前端与后端通信的契约来源：
 * - 字段名严格对应后端 SiteDTO / SiteCreateParams / SiteUpdateParams / SiteSearchParams
 * - 新增字段必须前后端同步变更
 * - 不要在此文件中放入表单校验逻辑；校验逻辑放 schemas/site.schema.ts
 */

/// 可用性状态（与后端 SiteStatus 枚举保持同名字符串）
export type SiteStatus = 'UNKNOWN' | 'UP' | 'DOWN'

/// 站点信息（接口返回结构）
export interface SiteDto {
  /// 主键
  id: number
  /// 站点名称（唯一，1-128 字符）
  name: string
  /// 站点 URL（唯一，必须 http:// 或 https:// 开头）
  url: string
  /// 所属分类 ID（每个站点必属一个分类，后端兜底为"默认分类"）
  categoryId: number
  /// 分类显示名（仅展示用，后端未填充时为 undefined）
  categoryName?: string
  /// 分类面包屑路径，如 "默认分类 / 浙江 / 杭州"（仅展示用）
  categoryPath?: string
  /// 可用性状态，未检测时为 null
  availabilityStatus?: SiteStatus | null
  /// 上次检测时间戳（毫秒），未检测为 null
  lastCheckedAt?: number | null
  /// 证书到期时间戳（毫秒）
  certificateExpiresAt?: number | null
  /// 域名到期时间戳（毫秒）
  domainExpiresAt?: number | null
  /// 证书签发机构
  certificateIssuer?: string | null
  /// 是否暂停监控（true = 该站点不参与扫描）
  paused: boolean
  /// 创建时间戳（毫秒）
  createdAt: number
  /// 更新时间戳（毫秒）
  updatedAt: number
  /// 站点级连续失败阈值覆盖；null = 走全局默认（详见 alert-confirm-setting）
  consecutiveFailuresBeforeAlert?: number | null
  /// 是否放过证书链不完整（PKIX path building failed）。默认 false。
  certForgiveChainIncomplete?: boolean
  /// 是否放过域名不匹配（证书 SAN/CN 与访问 host 不一致）。默认 false。
  certForgiveDomainMismatch?: boolean
  /// 是否放过自签证书（issuer DN == subject DN）。默认 false。
  certForgiveSelfSigned?: boolean
  /// 运维时段运行态(只读计算字段,后端回填)。NONE=未启用 / ACTIVE=运维进行中 / SCHEDULED=计划态。
  maintenanceStatus?: MaintenanceStatus
  /// 运维时段原始 JSON,仅编辑回显/提交用。未启用为 undefined。
  maintenance?: string
}

/// 运维时段运行态(只读):前端徽标/状态展示用。
export type MaintenanceStatus = 'NONE' | 'ACTIVE' | 'SCHEDULED'

/// 创建入参
export interface SiteCreateParams {
  name: string
  url: string
  /// 所属分类 ID；省略时后端兜底为"默认分类"
  categoryId?: number
  /// 站点级连续失败阈值覆盖；null/省略 = 走全局默认
  consecutiveFailuresBeforeAlert?: number | null
  /// 是否放过证书链不完整；省略 = 走全局默认 false
  certForgiveChainIncomplete?: boolean
  /// 是否放过域名不匹配；省略 = 走全局默认 false
  certForgiveDomainMismatch?: boolean
  /// 是否放过自签证书；省略 = 走全局默认 false
  certForgiveSelfSigned?: boolean
  /// 运维时段,JSON 对象字符串;例 {"start":"22:00","end":"08:00","days":["MON","TUE"]};省略/undefined = 不设置(沿用未启用)。
  maintenance?: string
}

/// 更新入参（包含 ID 标识）
export interface SiteUpdateParams {
  id: number
  name: string
  url: string
  /// 所属分类 ID；省略时保持当前分类不变
  categoryId?: number
  /// 站点级连续失败阈值覆盖；null = 走全局默认；undefined = 不变更
  consecutiveFailuresBeforeAlert?: number | null
  /// 是否放过证书链不完整；null = 不修改
  certForgiveChainIncomplete?: boolean | null
  /// 是否放过域名不匹配；null = 不修改
  certForgiveDomainMismatch?: boolean | null
  /// 是否放过自签证书；null = 不修改
  certForgiveSelfSigned?: boolean | null
  /// 运维时段,JSON 对象字符串;例 {"start":"22:00","end":"08:00","days":["MON","TUE"]};null = 不修改当前配置。
  maintenance?: string | null
  /// PATCH 语义下的"取消运维时段"信号。true = 清空 maintenance 字段(关闭);false/undefined = 不修改。
  unsetMaintenance?: boolean
}

/// 搜索条件（不含分页字段，分页由 useSearchPagination 注入）
export interface SiteSearchParams {
  /// 名称模糊匹配关键字
  keyword?: string
  /// 状态精确过滤
  availabilityStatus?: SiteStatus
  /// 按分类过滤（后端自动展开为含全部子分类）
  categoryId?: number
}
