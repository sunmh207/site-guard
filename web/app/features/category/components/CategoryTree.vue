<script setup lang="ts">
/// 分类树（左栏）。
///
/// 渲染：自写递归（避免引入 UTree，依赖更少）。
/// 行为：
///   - 选中节点 emit('select', id)
///   - 右键节点 emit('context-menu', node, event)
///   - 接收 site 拖拽：emit('drop-sites', siteIds, targetId)
///   - 接收分类拖拽：emit('category-reorder', sourceId, targetId, before)
///     跨父级 drop 直接 no-op（reorder 仅平级）
///
/// 视觉：外层 div 加 class="group" —— 子节点的手柄 ⋮⋮ 在 group-hover 时显出。
///
/// 指示线协调：所有 Node 共享一个 provide'd ref (`activeIndicatorId` + `activeIndicatorSide`)，
/// dragover 时各 Node 写入自己；这样鼠标跨 Node 移动时旧 Node 的指示线能被新 Node 覆盖清掉。
import { onMounted, provide, ref } from 'vue'
import type { CategoryTreeNode } from '../types/category.dto'

const props = defineProps<{
  tree: CategoryTreeNode[]
  selectedId: number | null
}>()

const emit = defineEmits<{
  select: [id: number]
  'context-menu': [node: CategoryTreeNode, event: MouseEvent]
  'drop-sites': [siteIds: number[], targetCategoryId: number]
  'category-reorder': [sourceId: number, targetId: number, before: boolean]
}>()

const expanded = ref<Set<number>>(new Set())

/// 跨 Node 共享的"当前显示指示线的目标"——保证一次只有一个 Node 画蓝线
const activeIndicatorId = ref<number | null>(null)
const activeIndicatorSide = ref<'before' | 'after' | null>(null)
provide('categoryTreeIndicator', { activeIndicatorId, activeIndicatorSide })

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

function onDropSites(siteIds: number[], targetId: number) {
  emit('drop-sites', siteIds, targetId)
}

function onCategoryDrop(sourceId: number, sourceParent: string, targetId: number, targetParent: number | null, before: boolean) {
  /// 跨父级 drop 直接 no-op
  const targetParentKey = targetParent == null ? 'root' : String(targetParent)
  if (sourceParent !== targetParentKey) return
  emit('category-reorder', sourceId, targetId, before)
}
</script>

<template>
  <!--
    group：让子节点的手柄在容器 hover 时显出（手柄用 group-hover:opacity-100）。
  -->
  <div class="text-sm select-none group">
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
      @drop-sites="onDropSites"
      @category-drop="onCategoryDrop"
    />
  </div>
</template>
