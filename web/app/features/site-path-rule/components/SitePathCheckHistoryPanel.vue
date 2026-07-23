<script setup lang="ts">
/// 子路由检测历史面板（bare 模式）。
///
/// 用法：
///   <SitePathCheckHistorySlideover v-model:open="open" :site-id="row.id" :rule-id="ruleId" :rule-path="path">
///     → 外层 slideover 嵌入本组件作为 body；本组件不渲染外层 USlideover/header/footer，
///       仅负责"拉取 + 渲染 30 条历史表格"。
///
/// 数据生命周期：
/// - onMounted 自动拉一次（首次打开即触发）
/// - 父组件可通过 ref.refresh() 主动重拉（用于 slideover header 的"刷新"按钮）
/// - 暴露 loading 给父组件用于刷新按钮的 spinner；暴露 lastRefreshedAt 给 header 显示绝对时间
///
/// 列表项：
/// - 5 列：探测时间 / 状态 / HTTP 状态码 / 关键字命中 / 错误信息
/// - 关键字命中列：仅 KEYWORD 模式有意义，HTTP_STATUS 模式显示"—"
import { onMounted, ref } from 'vue'
import { adminSitePathCheckHistoryApi } from '../api/site-path-check-history.api'
import type { SitePathCheckHistoryDto } from '../types/site-path-check-history.dto'
import type { CheckStatus } from '~/features/site/types/check-status'

const props = defineProps<{
  /// 目标路径规则 ID（必填）
  ruleId: number
}>()

/// 加载/数据/错误状态。loading 用 ref 而非 computed，让父组件 refresh() 时也能驱动按钮 spinner。
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const rows = ref<SitePathCheckHistoryDto[]>([])

/// 最后刷新成功的绝对时间戳（毫秒）。用于 slideover header 显示。
/// 失败时不更新，避免向用户展示与实际数据不一致的"最后刷新"时间。
const lastRefreshedAt = ref<number | null>(null)

/// 状态 → 颜色/标签/图标的映射。
/// 历史视角下只有两类：UP（探测完成拿到响应）/ ERROR（探测本身失败：超时/连接失败/SSL 等）。
function statusMeta(status: CheckStatus): { label: string, color: 'success' | 'error' | 'warning', icon: string } {
  switch (status) {
    case 'UP':
      return { label: '完成', color: 'success', icon: 'i-lucide-check-circle' }
    case 'DOWN':
      return { label: '失败', color: 'error', icon: 'i-lucide-x-circle' }
    case 'TIMEOUT':
      return { label: '超时', color: 'warning', icon: 'i-lucide-timer' }
    case 'ERROR':
      return { label: '错误', color: 'error', icon: 'i-lucide-alert-triangle' }
  }
}

/// 绝对时间格式化（YYYY-MM-DD HH:mm:ss），符合项目记忆 feedback_absolute_last_refresh_time。
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
    rows.value = await adminSitePathCheckHistoryApi.listRecent(props.ruleId, 30)
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
import type { SitePathCheckHistoryDto } from '../types/site-path-check-history.dto'

const columns: Array<{ accessorKey: keyof SitePathCheckHistoryDto; header: string }> = [
  { accessorKey: 'checkedAt', header: '探测时间' },
  { accessorKey: 'status', header: '状态' },
  { accessorKey: 'httpStatus', header: 'HTTP 状态码' },
  { accessorKey: 'textMatched', header: '关键字命中' },
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
      <template #textMatched-cell="{ row }">
        <!--
          textMatched 语义：true=命中 / false=未命中 / null=非 KEYWORD 模式或探测失败。
          仅 true/false 时渲染 badge，其余显示"—"。
        -->
        <UBadge
          v-if="row.original.textMatched === true"
          color="success"
          variant="subtle"
        >
          命中
        </UBadge>
        <UBadge
          v-else-if="row.original.textMatched === false"
          color="error"
          variant="subtle"
        >
          未命中
        </UBadge>
        <span v-else class="text-(--ui-text-muted)">—</span>
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
