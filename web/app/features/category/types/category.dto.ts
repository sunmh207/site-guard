/// 分类相关 DTO 定义

export interface CategoryTreeNode {
  id: number
  parentId: number | null
  name: string
  systemFlag: boolean
  seq: number
  siteCount: number
  children: CategoryTreeNode[]
}

export interface CategoryCreateParams {
  parentId: number | null
  name: string
}

export interface CategoryUpdateParams {
  id: number
  name?: string
  parentId?: number | null
  seq?: number
}

export interface CategoryDeleteParams {
  id: number
  fallbackId: number
}

/// 树拍平后给下拉用，path 用 " / " 拼接
export interface CategoryOption {
  value: number
  label: string
  depth: number
  systemFlag: boolean
}
