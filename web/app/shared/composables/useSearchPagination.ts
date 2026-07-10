import type { SelectItem } from '@nuxt/ui'

/**
 * 分页参数类型
 */
export interface Pager {
  /**
   * 当前页码（从1开始）
   */
  page: number
  /**
   * 每页显示条数
   */
  size: number
}

/**
 * useSearchPagination 配置选项
 */
export interface UseSearchPaginationOptions {
  /**
   * 默认的每页显示条数，默认为 10
   */
  defaultSize?: number
  /**
   * 每页显示条数的选项，默认为 [10, 50, 100]
   */
  pageSizeOptions?: number[]

  /**
   * 是否同步搜索条件和分页参数到 URL query 参数，默认为 true
   */
  syncUrlQueryParams?: boolean
}

/**
 * 通用的搜索分页逻辑
 *
 * 功能：
 * 1. 从 URL query 参数初始化搜索条件和分页参数
 * 2. 搜索条件和分页参数自动同步到 URL
 * 3. 搜索条件变化时自动重置分页到第一页
 * 4. 提供分页相关的计算属性和控制
 *
 * @param initialConditions 初始搜索条件（不包含 page 和 size）
 * @param options 配置选项
 * @returns 返回搜索条件、分页参数、分页控制等
 *
 * @example
 * ```ts
 * const { conditions, pager, pageSizeOptions } = useSearchPagination({
 *   id: '',
 *   keywords: ''
 * }, {
 *   defaultSize: 10,
 *   pageSizeOptions: [10, 50, 100]
 * });
 *
 * // 在模板中直接绑定 pager.size，修改时会自动重置到第一页
 * // <USelect v-model="pager.size" :items="pageSizeOptions" />
 * // pager.page 从1开始，可直接用于UI显示
 * ```
 */
export function useSearchPagination<T extends Record<string, any>>(
  initialConditions: T,
  options: UseSearchPaginationOptions = {},
) {
  const route = useRoute()
  const router = useRouter()

  const {
    defaultSize = 10,
    pageSizeOptions: customPageSizeOptions = [2, 10, 20, 50, 100],
    syncUrlQueryParams = true,
  } = options

  // 独立的分页参数
  const pager = reactive<Pager>({
    page: 1,
    size: defaultSize,
  })

  // 搜索条件（不包含 page 和 size）
  const conditions = reactive<T>({
    ...initialConditions,
  } as T)

  if (syncUrlQueryParams) {
    // 初始化：从 URL 读取搜索条件参数
    Object.keys(initialConditions).forEach((key) => {
      const queryValue = route.query[key]

      if (queryValue !== undefined && queryValue !== null) {
        const initialValue = initialConditions[key as keyof T]

        // 根据初始值的类型进行转换
        if (typeof initialValue === 'number') {
          ;(conditions as Record<string, unknown>)[key] = Number(queryValue)
        } else if (typeof initialValue === 'boolean') {
          ;(conditions as Record<string, unknown>)[key] = queryValue === 'true'
        } else {
          ;(conditions as Record<string, unknown>)[key] = queryValue
        }
      }
    })

    // 初始化：从 URL 读取分页参数
    if (route.query.page !== undefined) {
      pager.page = Number(route.query.page)
    }
    if (route.query.size !== undefined) {
      pager.size = Number(route.query.size)
    }
  }

  /**
   * 将搜索条件和分页参数同步到 URL query 参数
   */
  const syncConditionsToUrl = async () => {
    if (!syncUrlQueryParams) {
      return
    }

    const query: Record<string, string> = {}

    // 遍历搜索条件的所有字段
    Object.keys(conditions).forEach((key) => {
      const value = (conditions as Record<string, unknown>)[key]

      // 将值转换为字符串
      if (value !== undefined && value !== null && value !== '') {
        query[key] = String(value)
      }
    })

    // 添加分页参数（始终保留）
    query.page = String(pager.page)
    query.size = String(pager.size)

    await router.push({ query })
  }

  // 监听搜索条件变化，自动重置 page 为 1
  watch(
    () => ({ ...conditions }),
    async (newVal, oldVal) => {
      // 只有当值真正发生变化时才重置 page
      if (oldVal && JSON.stringify(newVal) !== JSON.stringify(oldVal)) {
        pager.page = 1
        await syncConditionsToUrl()
      }
    },
    { deep: true },
  )

  // 监听 pager.size 变化，自动重置到第一页
  watch(
    () => pager.size,
    (newSize, oldSize) => {
      if (oldSize !== undefined && newSize !== oldSize) {
        pager.page = 1
      }
    },
  )

  // 监听分页参数的变化，同步到 URL
  watch(
    () => ({ ...pager }),
    async (newVal, oldVal) => {
      if (oldVal && (newVal.page !== oldVal.page || newVal.size !== oldVal.size)) {
        await syncConditionsToUrl()
      }
    },
  )

  // 每页显示条数选项
  const pageSizeItems = computed<SelectItem[]>(() => {
    return customPageSizeOptions.map((size) => ({
      label: `${size} 条/页`,
      value: size,
    }))
  })

  /**
   * 计算总页数
   * @param total 总记录数
   */
  const getTotalPages = (total: number) => {
    return Math.ceil(total / pager.size)
  }

  return {
    /**
     * 搜索条件（响应式对象，不包含 page 和 size）
     */
    conditions,
    /**
     * 分页参数（响应式对象，包含 page 和 size）
     * 注意：修改 pager.size 会自动重置到第一页
     */
    pager,
    /**
     * 每页显示条数的选项
     */
    pageSizeOptions: pageSizeItems,
    /**
     * 手动同步条件到 URL（通常不需要手动调用，会自动同步）
     */
    syncConditionsToUrl,
    /**
     * 计算总页数
     */
    getTotalPages,
  }
}
