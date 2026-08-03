/// 分类模块 API 客户端
import { $adminApi } from '~/api/admin-api-client'
import type { StatusResult } from '~/shared/types/api'
import type {
  CategoryCreateParams,
  CategoryDeleteParams,
  CategoryReorderItem,
  CategoryTreeNode,
  CategoryUpdateParams,
} from '../types/category.dto'

export const adminCategoryApi = {
  /// GET /category/tree
  async tree(): Promise<CategoryTreeNode[]> {
    return await $adminApi<CategoryTreeNode[]>('/category/tree')
  },

  /// POST /category/create
  async create(params: CategoryCreateParams): Promise<CategoryTreeNode> {
    return await $adminApi<CategoryTreeNode>('/category/create', {
      method: 'POST',
      body: params,
    })
  },

  /// POST /category/update
  async update(params: CategoryUpdateParams): Promise<CategoryTreeNode> {
    return await $adminApi<CategoryTreeNode>('/category/update', {
      method: 'POST',
      body: params,
    })
  },

  /// POST /category/delete
  async delete(params: CategoryDeleteParams): Promise<StatusResult<void>> {
    return await $adminApi<StatusResult<void>>('/category/delete', {
      method: 'POST',
      body: params,
    })
  },

  /// POST /category/reorder
  /// 调用方负责计算每个 item 的 seq（步长 100），本方法只做序列化与发送。
  /// 后端按 items 全量覆盖 seq；id 不存在时后端抛 NOT_FOUND。
  async reorder(items: CategoryReorderItem[]): Promise<StatusResult<void>> {
    return await $adminApi<StatusResult<void>>('/category/reorder', {
      method: 'POST',
      body: { items },
    })
  },
}
