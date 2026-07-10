<script setup lang="ts">
/// 分类树（左栏）。
///
/// 渲染：自写递归（避免引入 UTree，依赖更少）。
/// 行为：
///   - 选中节点 emit('select', id)
///   - 右键节点 emit('context-menu', node, event)
///   - 接收 site 拖拽：emit('drop-sites', siteIds, targetId)
import { onMounted, ref } from 'vue'
import type { CategoryTreeNode } from '../types/category.dto'

const props = defineProps<{
  tree: CategoryTreeNode[]
  selectedId: number | null
}>()

const emit = defineEmits<{
  select: [id: number]
  'context-menu': [node: CategoryTreeNode, event: MouseEvent]
  'drop-sites': [siteIds: number[], targetCategoryId: number]
}>()

const expanded = ref<Set<number>>(new Set())

/// 初始化时默认展开第一层
onMounted(() => {
  for (const n of props.tree) {
    expanded.value.add(n.id)
  }
})

function isExpanded(id: number) {
  return expanded.value.has(id)
}

function toggle(id: number) {
  if (expanded.value.has(id)) expanded.value.delete(id)
  else expanded.value.add(id)
}

function onClickNode(node: CategoryTreeNode) {
  emit('select', node.id)
}

/// 不调用 preventDefault，让 contextmenu 冒泡到外层 UContextMenu 的 trigger；
/// 由 reka-ui 的 trigger 自己在打开菜单时调用 preventDefault 抑制浏览器原生菜单。
function onContext(node: CategoryTreeNode, e: MouseEvent) {
  emit('context-menu', node, e)
}

function onDragOver(e: DragEvent) {
  if (e.dataTransfer?.types.includes('text/site-ids')) {
    e.preventDefault()
  }
}

function onDrop(node: CategoryTreeNode, e: DragEvent) {
  e.preventDefault()
  const raw = e.dataTransfer?.getData('text/site-ids')
  if (!raw) return
  try {
    const ids = JSON.parse(raw) as number[]
    if (Array.isArray(ids) && ids.length > 0) {
      emit('drop-sites', ids, node.id)
    }
  }
  catch {
    // 静默忽略
  }
}
</script>

<template>
  <div class="text-sm select-none">
    <CategoryTreeNode
      v-for="node in tree"
      :key="node.id"
      :node="node"
      :depth="0"
      :selected-id="selectedId"
      :is-expanded="isExpanded"
      @toggle="toggle"
      @click-node="onClickNode"
      @context-node="onContext"
      @dragover-node="onDragOver"
      @drop-node="onDrop"
    />
  </div>
</template>
