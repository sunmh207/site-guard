<script setup lang="ts">
/// 监控仪表盘页面。
///
/// - UDashboardPanel 包裹：保证主区域在 UDashboardGroup 中占满剩余宽度（避免右侧空白）
/// - UDashboardNavbar 承载标题、最后刷新时间与"刷新"按钮，leading 槽位放 UDashboardSidebarCollapse 以折叠侧边栏
/// - body 内拉取 GET /site/stats/dashboard 的响应，渲染 3 张摘要卡片（DashboardSummaryCards）+ 统一告警面板（RecentAlertsTable）
/// - 每 30 秒自动刷新一次（@vueuse/core 的 useIntervalFn）
import { useIntervalFn } from '@vueuse/core'
import { adminSiteStatsApi } from '~/features/site/api/site-stats.api'

definePageMeta({
  layout: 'admin',
})

const { data, refresh, status } = await adminSiteStatsApi.getDashboard()

/// 最后一次成功刷新的时间戳（毫秒）。手动点击 / 定时器 / 首次加载完成后都会更新。
/// 渲染为绝对时间 "YYYY-MM-DD HH:mm:ss"——页面冻结时仍能看到真实刷新时刻，
/// 不会被"刚刚"这种相对时间误导。
const lastRefreshedAt = ref<number | null>(data.value ? Date.now() : null)

/// 刷新并记录时间戳。手动 / 定时器都走这里，保持一处更新。
async function doRefresh() {
  await refresh()
  if (status.value === 'success') {
    lastRefreshedAt.value = Date.now()
  }
}

/// 把时间戳格式化为 "YYYY-MM-DD HH:mm:ss"。用于"最后刷新"展示绝对时间。
function formatTimestamp(ms: number | null): string {
  if (ms === null) return '—'
  const d = new Date(ms)
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
         `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/// 每 30 秒自动刷新一次。useIntervalFn 会在组件卸载时自动清理。
useIntervalFn(() => {
  doRefresh()
}, 30_000)
</script>

<template>
  <UDashboardPanel id="dashboard">
    <template #header>
      <UDashboardNavbar title="监控仪表盘">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <span
            v-if="lastRefreshedAt !== null"
            class="text-xs text-(--ui-text-muted) mr-2"
          >
            最后刷新：{{ formatTimestamp(lastRefreshedAt) }}
          </span>
          <UButton
            icon="i-lucide-refresh-cw"
            :loading="status === 'pending'"
            @click="doRefresh()"
          >
            刷新
          </UButton>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <DashboardSkeleton v-if="!data" />
      <template v-else>
        <DashboardSummaryCards :summary="data.summary" />
        <RecentAlertsTable :alerts="data.recentAlerts" />
      </template>
    </template>
  </UDashboardPanel>
</template>
