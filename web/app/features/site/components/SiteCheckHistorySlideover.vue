<script setup lang="ts">
/// 站点探测历史 slideover（薄外壳）。
///
/// 用法：
///   <SiteCheckHistorySlideover v-model:open="open" :site-id="row.id" :site-name="row.name" />
///
/// 设计要点：
/// - 与 SitePathRuleSlideover 同构：USlideover 外壳 + bare Panel 嵌入 + header 显示目标标题
/// - header 右侧的"刷新"按钮：调用 panel.refresh()；panel.loading 直接绑定给按钮 loading
/// - header 右侧的"最后刷新"：绝对时间 YYYY-MM-DD HH:mm:ss，符合项目记忆
/// - footer 仅"关闭"按钮；探测历史是只读视图，没有"保存"动作
import { ref } from 'vue'
import SiteCheckHistoryPanel from './SiteCheckHistoryPanel.vue'

const props = defineProps<{
  /// 目标站点 ID（传给 Panel）
  siteId: number
  /// 目标站点名称（用于 header 显示，区分同时存在的多个 slideover）
  siteName: string
}>()

const open = defineModel<boolean>('open', { default: false })

/// 引用嵌入的 Panel：调用其暴露的 refresh() 触发重新拉取
const panelRef = ref<InstanceType<typeof SiteCheckHistoryPanel> | null>(null)

/// 触发刷新：转发给 panel；loading 状态由 panel.loading 持有，按钮直接绑定即可
function onRefresh() {
  panelRef.value?.refresh()
}
</script>

<template>
  <USlideover
    v-model:open="open"
    :ui="{
      /// 5 列 UTable + 错误信息展示需要更宽；与 SitePathRuleSlideover 历史取值一致
      content: 'max-w-7xl',
      header: 'flex items-center justify-between px-6 py-4 border-b border-default',
      body: 'p-6',
      footer: 'flex items-center justify-end gap-3 px-6 py-4 border-t border-default',
    }"
  >
    <template #header>
      <h2 class="text-base font-medium">探测历史 — {{ props.siteName }}</h2>
      <!--
        右侧：刷新按钮 + 最后刷新时间 + 关闭按钮。
        三者顺序按"操作相关性"排列：操作按钮（刷新）紧邻标题，时间戳放中间，关闭放最右。
      -->
      <div class="flex items-center gap-3">
        <UButton
          icon="i-lucide-refresh-cw"
          label="刷新"
          color="neutral"
          variant="outline"
          size="sm"
          :loading="panelRef?.loading"
          :disabled="panelRef?.loading"
          @click="onRefresh"
        />
        <!--
          最后刷新时间：绝对时间格式，null 时显示"—"。
          注意：defineExpose 会自动解包 ref，所以模板里访问的是 number | null 而非 ref<number | null>，
          不能加 .value（TS 会报 "Property 'value' does not exist on type 'number'"）。
        -->
        <span class="text-xs text-(--ui-text-muted) tabular-nums">
          最后刷新：{{ panelRef?.lastRefreshedAt != null
            ? (() => {
                const d = new Date(panelRef.lastRefreshedAt as number)
                const pad = (n: number) => String(n).padStart(2, '0')
                return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
              })()
            : '—' }}
        </span>
        <UButton color="neutral" variant="ghost" icon="i-lucide-x" size="md" square @click="open = false" />
      </div>
    </template>

    <template #body>
      <!--
        bare 模式下 Panel 自身不渲染外壳，只输出表格 + 错误状态。
        用 v-if 让 panel 仅在 slideover 打开时挂载，避免一次性渲染 100 个站点行的 panel。
      -->
      <SiteCheckHistoryPanel
        v-if="open"
        ref="panelRef"
        :site-id="props.siteId"
      />
    </template>

    <template #footer>
      <!--
        只读视图：仅"关闭"按钮。关闭走 footer 右下角，与其他 slideover 操作习惯保持一致；
        header 的 X 按钮 + backdrop 点击也可关闭。
      -->
      <UButton label="关闭" color="neutral" variant="subtle" @click="open = false" />
    </template>
  </USlideover>
</template>