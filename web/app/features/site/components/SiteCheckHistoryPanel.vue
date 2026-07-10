<script setup lang="ts">
/// 站点探测历史面板（bare 模式）。
///
/// 用法：
///   <SiteCheckHistorySlideover v-model:open="open" :site-id="row.id" :site-name="row.name">
///     → 外层 slideover 嵌入本组件作为 body；本组件不渲染外层 USlideover/header/footer，
///       仅负责"拉取 + 渲染 30 条历史表格"。
///
/// 数据生命周期：
/// - onMounted 自动拉一次（首次打开即触发）
/// - 父组件可通过 ref.refresh() 主动重拉（用于 slideover header 的"刷新"按钮）
/// - 暴露 loading 给父组件用于刷新按钮的 spinner；暴露 lastRefreshedAt 给 header 显示绝对时间
///
/// 列表项：
/// - 5 列：探测时间 / 状态 / HTTP 状态码 / 响应耗时 / 错误信息
/// - 错误信息过长时折叠为 tooltip，避免撑高表格行
import { onMounted, ref } from 'vue'
import { adminSiteCheckHistoryApi } from '../api/site-check-history.api'
import type { SiteCheckHistoryDto } from '../types/check-history.dto'
import type { CheckStatus } from '../types/check-status'

const props = defineProps<{
  /// 目标站点 ID（必填）
  siteId: number
}>()

/// 加载/数据/错误状态。loading 用 ref 而非 computed，让父组件 refresh() 时也能驱动按钮 spinner。
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const rows = ref<SiteCheckHistoryDto[]>([])

/// 最后刷新成功的绝对时间戳（毫秒）。用于 slideover header 显示。
/// 失败时不更新，避免向用户展示与实际数据不一致的"最后刷新"时间。
const lastRefreshedAt = ref<number | null>(null)

/// 状态 → 颜色/标签/图标的映射。TIMEOUT 与 ERROR 在站点侧都被归一为 DOWN，
/// 但在历史视角下需要区分：TIMEOUT 是"超时尚未完成"，ERROR 是"网络层失败"。
function statusMeta(status: CheckStatus): { label: string, color: 'success' | 'error' | 'warning', icon: string } {
  switch (status) {
    case 'UP':
      return { label: '成功', color: 'success', icon: 'i-lucide-check-circle' }
    case 'DOWN':
      return { label: '失败', color: 'error', icon: 'i-lucide-x-circle' }
    case 'TIMEOUT':
      return { label: '超时', color: 'warning', icon: 'i-lucide-timer' }
    case 'ERROR':
      return { label: '错误', color: 'error', icon: 'i-lucide-alert-triangle' }
  }
}

/// 绝对时间格式化（YYYY-MM-DD HH:mm:ss），符合项目记忆 feedback_absolute_last_refresh_time。
/// 与 SitePathRulePanel 的实现保持一致；冻结页面不撒谎。
function formatTimestamp(ms: number | null): string {
  if (ms == null) return '—'
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/// 拉取最新历史。提供给父组件调用的 refresh() 与 onMounted 共享同一段逻辑。
async function refresh() {
  loading.value = true
  errorMessage.value = null
  try {
    rows.value = await adminSiteCheckHistoryApi.listRecent(props.siteId, 30)
    lastRefreshedAt.value = Date.now()
  }
  catch (e) {
    errorMessage.value = (e as Error)?.message ?? '加载失败'
    /// 注意：失败时不更新 lastRefreshedAt，让 header 仍显示上一次成功刷新的时间
  }
  finally {
    loading.value = false
  }
}

/// 把 refresh / loading / lastRefreshedAt 暴露给父组件（slideover header 用到后两者）。
defineExpose({
  refresh,
  loading,
  lastRefreshedAt,
})

onMounted(refresh)
</script>

<script lang="ts">
/// 列表列定义（与模板中的 cell slot 一一对应）。
/// 单独放一个 <script lang="ts"> 而非合并进 setup：和 SitePathRulePanel.vue 的风格一致，
/// 避免在 setup 内 import 类型只为声明列元数据。
/// 注意：SiteCheckHistoryDto 类型已在上面 <script setup> 里 import；本块直接引用即可，
/// 否则在同一个 SFC 里重复 import 同一标识符会触发 TS2300。
import type { SiteCheckHistoryDto } from '../types/check-history.dto'

const columns: Array<{ accessorKey: keyof SiteCheckHistoryDto; header: string }> = [
  { accessorKey: 'checkedAt', header: '探测时间' },
  { accessorKey: 'status', header: '状态' },
  { accessorKey: 'httpStatus', header: 'HTTP 状态码' },
  { accessorKey: 'responseMs', header: '响应耗时 (ms)' },
  { accessorKey: 'errorMessage', header: '错误信息' },
]
</script>

<template>
  <div class="space-y-3">
    <!--
      加载中：显示文本而非 spinner，避免与 slideover header 的"刷新"按钮 spinner 视觉重复。
      错误：红色文本，给出 message。
    -->
    <div v-if="loading && rows.length === 0" class="text-default">加载中...</div>
    <div v-else-if="errorMessage && rows.length === 0" class="text-error">
      加载失败：{{ errorMessage }}
    </div>
    <div v-else-if="rows.length === 0" class="text-default">暂无探测历史</div>

    <UTable v-else :data="rows" :columns="columns">
      <template #checkedAt-cell="{ row }">
        <span class="tabular-nums">{{ formatTimestamp(row.original.checkedAt) }}</span>
      </template>
      <template #status-cell="{ row }">
        <!--
          把 status 渲染为带颜色/图标的 badge。
          meta 在 setup 里已根据 4 态分情况生成；此处只消费、不再做分支判断。
        -->
        <UBadge
          :color="statusMeta(row.original.status).color"
          :icon="statusMeta(row.original.status).icon"
          variant="subtle"
          size="sm"
        >
          {{ statusMeta(row.original.status).label }}
        </UBadge>
      </template>
      <template #httpStatus-cell="{ row }">
        <span class="tabular-nums">{{ row.original.httpStatus ?? '—' }}</span>
      </template>
      <template #responseMs-cell="{ row }">
        <span class="tabular-nums">{{ row.original.responseMs ?? '—' }}</span>
      </template>
      <template #errorMessage-cell="{ row }">
        <!--
          长错误信息截断展示 + tooltip 显示全文。
          数据库字段限长 512，绝大多数场景不会超长；截断阈值 60 字符。
        -->
        <UTooltip
          v-if="row.original.errorMessage"
          :text="row.original.errorMessage"
        >
          <span class="text-default">
            {{ row.original.errorMessage.length > 60
              ? `${row.original.errorMessage.slice(0, 60)}…`
              : row.original.errorMessage }}
          </span>
        </UTooltip>
        <span v-else class="text-(--ui-text-muted)">—</span>
      </template>
    </UTable>

    <!--
      错误回显：覆盖"已有旧数据 + 本次刷新失败"的场景。
      与顶部"rows.length === 0"的错误回显区分：前者会替换表格，后者只是追加一条提示。
    -->
    <div v-if="errorMessage && rows.length > 0" class="text-error text-sm">
      刷新失败：{{ errorMessage }}
    </div>
  </div>
</template>