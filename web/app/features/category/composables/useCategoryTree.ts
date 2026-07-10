/// 分类树状态：拉取整树、拍平为 options、追踪选中节点
import { computed, ref } from 'vue'
import { adminCategoryApi } from '../api/category.api'
import type { CategoryOption, CategoryTreeNode } from '../types/category.dto'
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

  return { tree, selectedId, loading, options, refresh, select }
}
