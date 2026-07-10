<script setup lang="ts">
/// 右键菜单（基于 UContextMenu）。
///
/// 通过 props.node 决定显示哪些项；emit 让父页面去打开 slideover / 模态。
/// 当 node 为 null 时不渲染菜单（避免空 trigger 导致右键一直弹）。
import type { ContextMenuItem } from '@nuxt/ui'
import { computed } from 'vue'
import type { CategoryTreeNode } from '../types/category.dto'

const props = defineProps<{
  /// 当前右键命中的分类节点；null 表示尚未右键过任何节点
  node: CategoryTreeNode | null
  /// 所有分类节点（用于"移动到"子菜单）
  allNodes: CategoryTreeNode[]
}>()

const emit = defineEmits<{
  'create-child': [parent: CategoryTreeNode]
  rename: [node: CategoryTreeNode]
  delete: [node: CategoryTreeNode]
  'move-to': [node: CategoryTreeNode, targetId: number]
}>()

/// "移动到"可选目标：排除自身 + 自身后代
const moveTargets = computed(() => {
  const out: { id: number, label: string }[] = []
  // 先收集自身和所有后代的 id
  const blocked = new Set<number>()
  const collectBlocked = (n: CategoryTreeNode) => {
    blocked.add(n.id)
    n.children.forEach(collectBlocked)
  }
  if (props.node) collectBlocked(props.node)
  const walk = (nodes: CategoryTreeNode[], path: string[]) => {
    for (const n of nodes) {
      if (blocked.has(n.id)) continue
      const here = [...path, n.name]
      out.push({ id: n.id, label: here.join(' / ') })
      walk(n.children, here)
    }
  }
  walk(props.allNodes, [])
  return out
})

/// 根据 node 动态生成菜单项；node 为 null 时返回空数组（菜单不显示）
const items = computed<ContextMenuItem[]>(() => {
  const n = props.node
  if (!n) return []
  return [
    [
      { label: '新建子分类', icon: 'i-lucide-plus', onSelect: () => emit('create-child', n) },
      { label: '重命名', icon: 'i-lucide-pencil', onSelect: () => emit('rename', n) },
      {
        label: '移动到…',
        icon: 'i-lucide-corner-down-right',
        children: moveTargets.value.map(t => ({
          label: t.label,
          onSelect: () => emit('move-to', n, t.id),
        })),
      },
    ],
    [
      {
        label: '删除',
        icon: 'i-lucide-trash',
        color: 'error' as const,
        /// 系统分类或有子分类时不可删
        disabled: n.systemFlag || n.children.length > 0,
        onSelect: () => emit('delete', n),
      },
    ],
  ]
})
</script>

<template>
  <UContextMenu :items="items">
    <slot />
  </UContextMenu>
</template>
