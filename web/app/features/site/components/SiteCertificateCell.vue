<script setup lang="ts">
/// 站点证书到期展示组件。
///
/// 将后端返回的 certificateExpiresAt（毫秒时间戳）映射为带颜色和图标的 UBadge：
/// - 未设置 → 未检测（neutral）
/// - 已过期或不足 3 天 → 紧急（error）
/// - 不足 15 天 → 警告（warning）
/// - 其他      → 正常（success）
const props = defineProps<{
  /// 证书到期时间戳（毫秒）；缺失或 null 视为未检测
  expiresAt?: number | null
}>()

function daysLeft(ts?: number | null): number | null {
  if (!ts)
    return null
  return Math.floor((ts - Date.now()) / (1000 * 60 * 60 * 24))
}

const config = computed(() => {
  const days = daysLeft(props.expiresAt)
  if (days === null) {
    return { label: '未检测', color: 'neutral' as const, icon: 'i-lucide-help-circle' }
  }
  if (days < 3) {
    return {
      label: days < 0 ? `已过期 ${-days} 天` : `${days} 天`,
      color: 'error' as const,
      icon: 'i-lucide-shield-alert',
    }
  }
  if (days < 15) {
    return { label: `${days} 天`, color: 'warning' as const, icon: 'i-lucide-clock' }
  }
  return { label: `${days} 天`, color: 'success' as const, icon: 'i-lucide-shield-check' }
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
