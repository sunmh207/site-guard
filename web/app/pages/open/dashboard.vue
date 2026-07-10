<script setup lang="ts">
/// 公开大屏 dashboard（无鉴权，仅内网）。
///
/// - 走 open layout：暗色、无侧边栏、无登录 UI
/// - 通过 openSiteStatsApi 调 GET /site/stats/dashboard，不带 Authorization
/// - 2 分钟自动轮询；useWakeLock 防止浏览器休眠断流
/// - 失败/掉线降级：保留「最后成功时间」，渲染错误角标而不是黑屏
/// - 404（管理员未开启）→ 显示「未开启」友好提示页，不展示任何数据，
///   避免「页面在但内容没数据」被外部观察者误认为可用
import { useIntervalFn, useFullscreen, useWakeLock } from '@vueuse/core'
import { openSiteStatsApi } from '~/features/site/api/open-stats.api'
import { useMessage } from '~/shared/composables/useMessage'
import { ROUTES } from '~/shared/constants/routes'

definePageMeta({ layout: 'open' })

const { data, refresh, status, error } = await openSiteStatsApi.getDashboard()

/// 404 / 关闭态判定：useFetch 失败后 error.value 会带 code / status。
/// 后端 AdminConfigController / OpenDashboardController 抛 Errors.NOT_FOUND，
/// 我们的 useApi 拦截器把它映射成 {data:{code:'NOT_FOUND',...}, statusCode:404}。
const isDisabledByAdmin = computed(() => {
  if (!error.value) return false
  if (error.value.data?.code === 'NOT_FOUND') return true
  if (error.value.statusCode === 404) return true
  if (error.value.response?.status === 404) return true
  return false
})

/// 最后一次成功刷新的时间戳（毫秒）。手动点击 / 定时器 / 首次加载完成后都会更新。
/// 渲染为绝对时间 "YYYY-MM-DD HH:mm:ss"——页面冻结时仍能看到真实刷新时刻。
const lastRefreshedAt = ref<number | null>(data.value && !isDisabledByAdmin.value ? Date.now() : null)

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

/// 每 2 分钟自动刷新一次。
useIntervalFn(() => {
  doRefresh()
}, 120_000)

/// 申请屏幕常亮（防休眠）。浏览器拒绝时静默降级。
/// 注意：关闭态（404）下不申请唤醒——访客看个提示页不该让屏幕常亮。
if (!isDisabledByAdmin.value) {
  useWakeLock()
}

/// 浏览器原生全屏。target = document.documentElement 让整个页面（含 navbar 与背景）
/// 进入全屏，与"大屏占满屏幕"语义一致。vueuse 自动监听 fullscreenchange 同步 isFullscreen。
const { isFullscreen, isSupported, toggle } = useFullscreen(document.documentElement)

/// 集中兜底：浏览器拒绝（如未由用户手势触发 / 权限策略拦截）时降级 toast，不污染后续状态。
const message = useMessage()

async function onToggleFullscreen() {
  try {
    await toggle()
  }
  catch (e: any) {
    message.error(e?.message || '进入全屏失败，请检查浏览器权限')
  }
}
</script>

<template>
  <UDashboardPanel id="open-dashboard">
    <template #header>
      <UDashboardNavbar title="Site Guard 监控大屏">
        <template #right>
          <span
            v-if="lastRefreshedAt !== null"
            class="text-sm text-(--ui-text-muted) mr-3"
          >
            最后刷新：{{ formatTimestamp(lastRefreshedAt) }}
          </span>
          <!-- 全屏按钮：仅数据已加载、未关闭、浏览器支持时显示；
               图标/文案按 isFullscreen 切换，匹配「未全屏→最大化」「已全屏→最小化」语义。 -->
          <UButton
            v-if="data && !isDisabledByAdmin && isSupported"
            variant="ghost"
            color="neutral"
            :icon="isFullscreen ? 'i-lucide-minimize-2' : 'i-lucide-maximize-2'"
            :aria-label="isFullscreen ? '退出全屏' : '进入全屏'"
            data-testid="open-dashboard-fullscreen-toggle"
            @click="onToggleFullscreen"
          >
            {{ isFullscreen ? '退出全屏' : '全屏' }}
          </UButton>
          <span
            v-if="error && !isDisabledByAdmin"
            class="text-sm text-error"
          >
            ⚠ 数据获取失败
          </span>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <!-- 关闭态（管理员未开启）：友好提示页。这是设计目标的关键页面之一：
           不存在 → 显示「此功能未开启」，不露出任何数据；避免 /open/dashboard
           在外网暴露但看起来「数据空了」的歧义状态。 -->
      <div
        v-if="isDisabledByAdmin"
        class="flex items-center justify-center min-h-[60vh] p-6"
        data-testid="open-dashboard-disabled"
      >
        <UCard class="max-w-md w-full">
          <template #header>
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-monitor-x" class="w-6 h-6 text-muted" />
              <h1 class="text-lg font-medium">公开大屏未开启</h1>
            </div>
          </template>

          <p class="text-sm text-muted leading-6">
            此监控大屏当前由管理员关闭。如需查看实时监控数据，
            请联系管理员在管理后台「设置 → 显示」中开启。
          </p>

          <!-- 按钮靠右：与上方左对齐的提示文案形成视觉分隔，避免「按钮像是文案的一部分」 -->
          <div class="mt-4 flex justify-end">
            <UButton
              :to="ROUTES.LOGIN"
              variant="outline"
              color="neutral"
            >
              前往登录
            </UButton>
          </div>
        </UCard>
      </div>

      <DashboardSkeleton v-else-if="!data" />
      <template v-else>
        <DashboardSummaryCards :summary="data.summary" />
        <RecentAlertsTable :alerts="data.recentAlerts" />
      </template>
    </template>
  </UDashboardPanel>
</template>