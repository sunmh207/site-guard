/// 分类树状态：拉取整树、拍平为 options、追踪选中节点
import { computed, ref } from 'vue'
import { adminCategoryApi } from '../api/category.api'
import type { CategoryOption, CategoryReorderItem, CategoryTreeNode } from '../types/category.dto'
import { useMessage } from '~/shared/composables/useMessage'

export function useCategoryTree() {
  const tree = ref<CategoryTreeNode[]>([])
  const selectedId = ref<number | null>(null)
  const loading = ref(false)
  const message = useMessage()

  async function refresh() {
    loading.value = true
    try {
      tree.value = await adminCategoryApi.tree()
      if (selectedId.value == null && tree.value.length > 0) {
        selectedId.value = tree.value[0].id
      }
    }
    catch {
      message.error('分类树加载失败')
    }
    finally {
      loading.value = false
    }
  }

  /// 拍平树为带 path 的下拉选项
  const options = computed<CategoryOption[]>(() => {
    const out: CategoryOption[] = []
    const walk = (nodes: CategoryTreeNode[], path: string[], depth: number) => {
      for (const n of nodes) {
        const here = [...path, n.name]
        out.push({
          value: n.id,
          label: here.join(' / '),
          depth,
          systemFlag: n.systemFlag,
        })
        walk(n.children, here, depth + 1)
      }
    }
    walk(tree.value, [], 0)
    return out
  })

  function select(id: number) {
    selectedId.value = id
  }

  /// 在指定父级下重新排序兄弟节点（仅平级）。
  ///
  /// 行为：
  /// - orderedIds 是新顺序的完整兄弟 ID 列表（不是只发被拖的两个）
  /// - 先做乐观更新：本地 tree 立即按 orderedIds 重排同父下 children
  /// - seq 步长 100（1→100, 2→200, ...），给后续插入留 99 个空位
  /// - API 失败 → refresh() 重新拉树覆盖本地乐观更新
  /// - parentId=null 表示根级
  async function reorderWithinParent(parentId: number | null, orderedIds: number[]): Promise<void> {
    if (orderedIds.length === 0) return

    /// 找到目标兄弟列表的引用：根级 → tree.value；子级 → 父节点.children
    const target = locateSiblings(tree.value, parentId)
    if (!target) return

    /// 构造 id → 原顺序 索引，用于重排
    const oldOrder = target.map(n => n.id)
    const oldIndex = new Map<number, number>()
    oldOrder.forEach((id, i) => oldIndex.set(id, i))

    /// 乐观重排：按 orderedIds 重新组装 target
    const reordered = orderedIds
      .map((id) => {
        const idx = oldIndex.get(id)
        return idx == null ? null : target[idx]
      })
      .filter((n): n is CategoryTreeNode => n !== null)

    /// 防御：orderedIds 缺失某些兄弟时保留原位
    if (reordered.length !== target.length) {
      const inOrdered = new Set(orderedIds)
      for (const n of target) {
        if (!inOrdered.has(n.id)) reordered.push(n)
      }
    }

    const snapshot = target.slice()
    target.splice(0, target.length, ...reordered)

    const items: CategoryReorderItem[] = orderedIds.map((id, i) => ({ id, seq: (i + 1) * 100 }))
    try {
      await adminCategoryApi.reorder(items)
    }
    catch {
      target.splice(0, target.length, ...snapshot)
      message.error('调序失败')
      await refresh()
    }
  }

  return { tree, selectedId, loading, options, refresh, select, reorderWithinParent }
}

/// 在 tree 中定位指定父级下的兄弟数组引用；parentId=null → 根级 tree.value。
/// 返回 null 表示父级不存在（被外部删除等情况，调用方应 no-op）。
function locateSiblings(tree: CategoryTreeNode[], parentId: number | null): CategoryTreeNode[] | null {
  if (parentId == null) return tree
  const stack: CategoryTreeNode[] = tree.slice()
  while (stack.length > 0) {
    const n = stack.pop()!
    if (n.id === parentId) return n.children
    if (n.children.length > 0) stack.push(...n.children)
  }
  return null
}
