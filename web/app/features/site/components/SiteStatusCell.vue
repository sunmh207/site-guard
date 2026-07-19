<script setup lang="ts">
/// 站点可用性状态展示组件。
///
/// 将后端返回的 SiteStatus（UNKNOWN / UP / DOWN / null）映射为带颜色和图标的 UBadge。
/// - paused=true      → 已暂停（中性灰，最优先，盖在 availability 之上）
/// - UNKNOWN 或 null  → 未检测（中性灰）
/// - UP               → 在线（成功绿）
/// - DOWN             → 离线（错误红）
import type { MaintenanceStatus, SiteStatus } from '../types/site.dto'

const props = defineProps<{
  /// 当前可用性状态；缺失或 null 视为未检测
  status?: SiteStatus | null
  /// 是否暂停监控（true = 显示"已暂停"，覆盖 status 显示）
  paused?: boolean
  /// 运维时段运行态:ACTIVE = 此刻正落在运维窗口内,显示"运维中"徽标(覆盖 status 显示)
  maintenanceStatus?: MaintenanceStatus | null
}>()

const config = computed(() => {
  // 已暂停:最高优先级,盖在一切之上(站长决策不再监控该站点,值班无需关注其可用性/运维状态)
  if (props.paused) {
    return { label: '已暂停', color: 'neutral' as const, icon: 'i-lucide-pause-circle' }
  }
  // 运维进行中:站点仍活跃,临时静默(值班应知晓"此刻正常",避免误报)
  if (props.maintenanceStatus === 'ACTIVE') {
    return { label: '运维中', color: 'info' as const, icon: 'i-lucide-moon' }
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