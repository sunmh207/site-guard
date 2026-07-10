<script setup lang="ts">
/// 站点可用性状态展示组件。
///
/// 将后端返回的 SiteStatus（UNKNOWN / UP / DOWN / null）映射为带颜色和图标的 UBadge。
/// - paused=true      → 已暂停（中性灰，最优先，盖在 availability 之上）
/// - UNKNOWN 或 null  → 未检测（中性灰）
/// - UP               → 在线（成功绿）
/// - DOWN             → 离线（错误红）
import type { SiteStatus } from '../types/site.dto'

const props = defineProps<{
  /// 当前可用性状态；缺失或 null 视为未检测
  status?: SiteStatus | null
  /// 是否暂停监控（true = 显示"已暂停"，覆盖 status 显示）
  paused?: boolean
}>()

const config = computed(() => {
  if (props.paused) {
    return { label: '已暂停', color: 'neutral' as const, icon: 'i-lucide-pause-circle' }
  }
  if (!props.status || props.status === 'UNKNOWN') {
    return { label: '未检测', color: 'neutral' as const, icon: 'i-lucide-help-circle' }
  }
  if (props.status === 'UP') {
    return { label: '在线', color: 'success' as const, icon: 'i-lucide-check-circle' }
  }
  return { label: '离线', color: 'error' as const, icon: 'i-lucide-x-circle' }
})
</script>

<template>
  <UBadge
    :color="config.color"
    :icon="config.icon"
    variant="subtle"
    size="sm"
  >
    {{ config.label }}
  </UBadge>
</template>