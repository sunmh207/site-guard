/**
 * 共享 API 类型定义
 */

export interface PagerPayload<T> {
  /**
   * 数据列表
   */
  data: T[]
  /**
   * 当前页码
   */
  page: number
  /**
   * 每页条目数量
   */
  size: number
  /**
   * 总条目数量
   */
  total: number
}

/**
 * 状态结果接口
 */
export interface StatusResult<T = any> {
  code: string
  data: T
  message: string | null
}

////////////////////////////////

/**
 * 分页结果
 */
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

/**
 * 列表查询参数
 */
export interface ListParams {
  page?: number
  pageSize?: number
  keyword?: string
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

/**
 * API 响应包装
 */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

/**
 * API 错误响应
 */
export interface ApiError {
  code: number
  message: string
  details?: any
}

/**
 * 后端标准错误响应格式
 */
export interface ApiErrorResponse {
  status: number
  code: string
  message: string
  timestamp: number
}

/**
 * 系统配置响应：后端通用 /config/get 接口的载荷结构。
 * 任何 feature 读写系统配置（key→JSON value）都用这个泛型。
 * 后端对应：com.siteguard.system.dto.ConfigResponse（java record）。
 */
export interface ConfigResponse<T = unknown> {
  key: string
  value: T
  updatedAt: number
}
