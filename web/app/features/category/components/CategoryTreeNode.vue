<script setup lang="ts">
/// 分类树的递归子节点：渲染单层节点 + 转发事件到父组件。
///
/// 行为：
/// - click 选中节点
/// - 右键节点（emit contextmenu，浏览器原生菜单由外层 UContextMenu 抑制）
/// - 展开/折叠子节点
/// - 拖动 ⋮⋮ 手柄调序（HTML5 DnD）
///   - 跨父级 drop：no-op（reorder 仅平级）
///   - 同父级 drop：根据 clientY 相对节点中线判定 before/after
/// - 接收站点拖入：emit('drop-sites', node, siteIds)
///
/// 视觉：手柄 ⋮⋮ 默认 opacity-0；外层树容器 hover 时显出（不需要单独的"排序模式"开关）。
/// 拖动期间（isDragging=true）手柄保持可见。
///
/// 指示线：用 provide/inject 共享 activeIndicatorId + activeIndicatorSide，
/// 保证整棵分类树同一时刻只有一条蓝线（鼠标跨 Node 移动时旧 Node 自动清除）。
import { computed, inject, ref, type Ref } from 'vue'
import type { CategoryTreeNode } from '../types/category.dto'

interface IndicatorState {
  activeIndicatorId: Ref<number | null>
  activeIndicatorSide: Ref<'before' | 'after' | null>
}

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
  'drop-sites': [siteIds: number[], targetCategoryId: number]
  /// 调序 drop：子组件已消化 sourceId / sourceParent / before
  'category-drop': [sourceId: number, sourceParent: string, targetId: number, targetParent: number | null, before: boolean]
}>()

const hasChildren = computed(() => props.node.children.length > 0)
const isSelected = computed(() => props.selectedId === props.node.id)
const paddingLeft = computed(() => `${props.depth * 14 + 4}px`)

const indicatorState = inject<IndicatorState>('categoryTreeIndicator')
const isDragging = ref(false)

/// 本节点是否当前显示指示线：从共享状态读
const indicatorSide = computed<'before' | 'after' | null>(() => {
  if (!indicatorState) return null
  if (indicatorState.activeIndicatorId.value !== props.node.id) return null
  return indicatorState.activeIndicatorSide.value
})

function setIndicator(side: 'before' | 'after' | null) {
  if (!indicatorState) return
  if (side === null) {
    /// 仅当自己当前是 active 时才清（避免误清别的 Node）
    if (indicatorState.activeIndicatorId.value === props.node.id) {
      indicatorState.activeIndicatorId.value = null
      indicatorState.activeIndicatorSide.value = null
    }
    return
  }
  indicatorState.activeIndicatorId.value = props.node.id
  indicatorState.activeIndicatorSide.value = side
}

function onToggle() { emit('toggle', props.node.id) }
function onClick() { emit('clickNode', props.node) }
function onContext(e: MouseEvent) { emit('contextNode', props.node, e) }

function onDragOver(e: DragEvent) {
  /// 分类调序 drop 区：row 必须在 dragover 中 preventDefault，否则 drop 不会触发
  if (e.dataTransfer?.types.includes('text/category-id')) {
    e.preventDefault()
    const target = e.currentTarget as HTMLElement
    const rect = target.getBoundingClientRect()
    const midY = rect.top + rect.height / 2
    setIndicator(e.clientY < midY ? 'before' : 'after')
    return
  }
  /// 站点拖入
  if (e.dataTransfer?.types.includes('text/site-ids')) {
    e.preventDefault()
  }
}

function onDrop(e: DragEvent) {
  /// 先尝试站点拖入
  const siteRaw = e.dataTransfer?.getData('text/site-ids')
  if (siteRaw) {
    e.preventDefault()
    try {
      const ids = JSON.parse(siteRaw) as number[]
      if (Array.isArray(ids) && ids.length > 0) {
        emit('drop-sites', ids, props.node.id)
      }
    }
    catch {
      // 静默忽略
    }
    setIndicator(null)
    return
  }
  /// 否则视为调序 drop
  const sourceId = Number(e.dataTransfer?.getData('text/category-id') || 0)
  const sourceParent = e.dataTransfer?.getData('text/category-parent') || ''
  if (sourceId && sourceParent && sourceId !== props.node.id) {
    e.preventDefault()
    const target = e.currentTarget as HTMLElement
    const rect = target.getBoundingClientRect()
    const midY = rect.top + rect.height / 2
    const before = e.clientY < midY
    emit('category-drop', sourceId, sourceParent, props.node.id, props.node.parentId, before)
  }
  setIndicator(null)
}

/// 手柄：只负责 dragstart（视觉手柄）；drop 命中区是 row 本身。
function onHandleDragStart(e: DragEvent) {
  if (!e.dataTransfer) return
  e.dataTransfer.setData('text/category-id', String(props.node.id))
  e.dataTransfer.setData('text/category-parent', props.node.parentId == null ? 'root' : String(props.node.parentId))
  e.dataTransfer.effectAllowed = 'move'
  isDragging.value = true
}

function onHandleDragEnd() {
  isDragging.value = false
  setIndicator(null)
}
</script>

<template>
  <div>
    <div
      :data-tree-node="node.id"
      class="flex items-center gap-1 py-1 pr-2 cursor-pointer rounded hover:bg-elevated"
      :class="{ 'bg-elevated text-highlighted': isSelected }"
      :style="{ paddingLeft }"
      :draggable="false"
      @click="onClick"
      @contextmenu="onContext"
      @dragover="onDragOver"
      @drop="onDrop"
    >
      <!--
        拖动手柄：始终渲染。默认 opacity-0 + group-hover:opacity-100 让树 hover 时显出；
        行 hover 时也显出（自己也要可拖）。
        拖动期间 isDragging 强制 opacity-100。
        阻止 click 冒泡到行 onClick（避免 toggle / select 误触）。
      -->
      <span
        class="w-4 h-4 flex items-center justify-center text-muted hover:text-highlighted cursor-grab active:cursor-grabbing select-none transition-opacity"
        :class="isDragging ? 'opacity-100' : 'opacity-0 group-hover:opacity-100 hover:!opacity-100 focus-visible:opacity-100'"
        draggable="true"
        title="拖动以调整顺序"
        @click.stop
        @dragstart="onHandleDragStart"
        @dragend="onHandleDragEnd"
      >⋮⋮</span>
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

    <!--
      指示线：上/下边缘各渲染一条 4px 蓝线。
      放在节点行容器外，避免影响 click 命中。
    -->
    <div
      v-if="indicatorSide === 'before'"
      class="h-1 -mt-0.5 bg-primary rounded-full"
      :style="{ marginLeft: `${depth * 14 + 4}px`, marginRight: '8px' }"
    />
    <div v-if="hasChildren && isExpanded(node.id)">
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
        @drop-sites="(ids, targetId) => emit('drop-sites', ids, targetId)"
        @category-drop="(s, sp, t, tp, b) => emit('category-drop', s, sp, t, tp, b)"
      />
    </div>
    <div
      v-if="indicatorSide === 'after'"
      class="h-1 -mb-0.5 bg-primary rounded-full"
      :style="{ marginLeft: `${depth * 14 + 4}px`, marginRight: '8px' }"
    />
  </div>
</template>
