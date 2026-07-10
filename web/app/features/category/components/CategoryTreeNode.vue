<script setup lang="ts">
/// 分类树的递归子节点：渲染单层节点 + 转发事件到父组件。
import { computed } from 'vue'
import type { CategoryTreeNode } from '../types/category.dto'

const props = defineProps<{
  node: CategoryTreeNode
  depth: number
  selectedId: number | null
  isExpanded: (id: number) => boolean
}>()

const emit = defineEmits<{
  toggle: [id: number]
  clickNode: [node: CategoryTreeNode]
  contextNode: [node: CategoryTreeNode, event: MouseEvent]
  dragoverNode: [event: DragEvent]
  dropNode: [node: CategoryTreeNode, event: DragEvent]
}>()

const hasChildren = computed(() => props.node.children.length > 0)
const isSelected = computed(() => props.selectedId === props.node.id)
const paddingLeft = computed(() => `${props.depth * 14 + 4}px`)

function onToggle() { emit('toggle', props.node.id) }
function onClick() { emit('clickNode', props.node) }
/// 不调用 preventDefault，让 contextmenu 冒泡到外层 UContextMenu 的 trigger；
/// 由 reka-ui 的 trigger 自己在打开菜单时调用 preventDefault 抑制浏览器原生菜单。
function onContext(e: MouseEvent) { emit('contextNode', props.node, e) }
function onDragOver(e: DragEvent) { emit('dragoverNode', e) }
function onDrop(e: DragEvent) { emit('dropNode', props.node, e) }
</script>

<template>
  <div>
    <div
      :data-tree-node="node.id"
      class="flex items-center gap-1 py-1 px-2 cursor-pointer rounded hover:bg-elevated"
      :class="{ 'bg-elevated text-highlighted': isSelected }"
      :style="{ paddingLeft }"
      @click="onClick"
      @contextmenu="onContext"
      @dragover="onDragOver"
      @drop="onDrop"
    >
      <button
        v-if="hasChildren"
        class="w-4 h-4 flex items-center justify-center text-muted hover:text-highlighted"
        @click.stop="onToggle"
      >
        {{ isExpanded(node.id) ? '▾' : '▸' }}
      </button>
      <span v-else class="w-4 h-4" />
      <span class="truncate flex-1">{{ node.name }}</span>
      <span v-if="node.siteCount > 0" class="text-xs text-muted">({{ node.siteCount }})</span>
    </div>
    <div v-if="isExpanded(node.id) && hasChildren">
      <CategoryTreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :depth="depth + 1"
        :selected-id="selectedId"
        :is-expanded="isExpanded"
        @toggle="(id) => emit('toggle', id)"
        @click-node="(n) => emit('clickNode', n)"
        @context-node="(n, e) => emit('contextNode', n, e)"
        @dragover-node="(e) => emit('dragoverNode', e)"
        @drop-node="(n, e) => emit('dropNode', n, e)"
      />
    </div>
  </div>
</template>
