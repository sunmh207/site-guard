<script setup lang="ts">
/// 仪表盘统一告警面板组件。
///
/// 接收 [RecentAlert]（来自 GET /site/stats/dashboard 的 recentAlerts 字段），
/// 渲染空态或 UTable 表格。
///
/// 表格列：
/// - 站点 : siteName + 内联可点击 siteUrl（点击新页面打开）
/// - 类型 : kind → 中文 + 图标（前端常量映射）
/// - 信息 : message 单行截断，hover 显示完整
/// - 时间 : detectedAt 相对时间
///
/// 状态列已移除：聚合源只输出 ABNORMAL，列恒为同色，冗余。
///
/// 后端已按 detectedAt DESC 排序；前端不再二次排序。
///
/// 表格外层用 max-h-[60vh] + overflow-y-auto 包一层：异常超过一屏时只滚表格体，
/// UCard 头部"异常"标题保持可见，避免整页被表格撑得很长。
import type { AlertKind, RecentAlert } from '~/features/site/types/site-stats.dto'

defineProps<{
  alerts: RecentAlert[]
}>()

/// 把检测时间戳渲染为相对时间字符串（"刚刚" / "N 分钟前" / "N 小时前" / "N 天前"）
function relativeTime(ms: number): string {
  const diff = Date.now() - ms
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return `${Math.floor(diff / 86_400_000)} 天前`
}

/// kind → 中文标签 + 图标。前端常量映射表，与后端 AlertKind 一一对应。
///
/// 任何后端新增 AlertKind 都必须同步在此表加一条，否则 RecentAlert.kind 命中未知值时
/// `KIND_META[row.original.kind].icon` 会抛 "Cannot read properties of undefined"
/// 并让 /admin/dashboard SSR 直接 500。
const KIND_META: Record<AlertKind, { label: string; icon: string }> = {
  AVAILABILITY:    { label: '可用性', icon: 'i-lucide-activity' },
  CERT_EXPIRY:     { label: '证书',  icon: 'i-lucide-shield-alert' },
  DOMAIN_EXPIRING: { label: '域名',  icon: 'i-lucide-calendar-x' },
  PATH_CHECK:      { label: '子路由', icon: 'i-lucide-route' },
}
</script>

<template>
  <UCard>
    <template #header>
      <h3 class="text-base font-semibold">异常</h3>
    </template>
    <div v-if="alerts.length === 0" class="text-center text-(--ui-text-muted) py-8">
      暂无异常
    </div>
    <UTable
      v-else
      :data="alerts"
      :columns="[
        { accessorKey: 'siteName',  header: '站点' },
        { accessorKey: 'kind',      header: '类型' },
        { accessorKey: 'message',   header: '信息' },
        { accessorKey: 'detectedAt', header: '时间' },
      ]"
      class="max-h-[60vh] overflow-y-auto"
    >
      <template #siteName-cell="{ row }">
        <span class="inline-flex items-baseline gap-2 min-w-0">
          <span class="truncate">{{ row.original.siteName }}</span>
          <a
            :href="row.original.siteUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="text-xs text-(--ui-primary) hover:underline truncate min-w-0"
            :title="row.original.siteUrl"
            @click.stop
          >
            {{ row.original.siteUrl }}
          </a>
        </span>
      </template>
      <template #kind-cell="{ row }">
        <span class="inline-flex items-center gap-1">
          <UIcon :name="KIND_META[row.original.kind].icon" class="size-4" />
          {{ KIND_META[row.original.kind].label }}
        </span>
      </template>
      <template #message-cell="{ row }">
        <span
          class="line-clamp-1 max-w-md"
          :title="row.original.message"
        >
          {{ row.original.message }}
        </span>
      </template>
      <template #detectedAt-cell="{ row }">
        <span :title="new Date(row.original.detectedAt).toLocaleString()">
          {{ relativeTime(row.original.detectedAt) }}
        </span>
      </template>
    </UTable>
  </UCard>
</template>