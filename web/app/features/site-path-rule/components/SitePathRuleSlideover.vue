<script setup lang="ts">
/// 站点子路由规则编辑抽屉。
///
/// 用法：
///   <SitePathRuleSlideover v-model:open="open" :site-id="row.id" :site-name="row.name" />
///
/// 与 SiteEditSlideover 解耦：站点基本信息编辑和子路由规则编辑各自独立，
/// 避免单个抽屉里出现两个"保存"按钮造成歧义。
/// 内部以 bare 模式嵌入 SitePathRulePanel，外壳与样式由本组件提供；
/// "保存"按钮统一放在 footer 右下角，与 SiteEditSlideover 等操作习惯一致。
import { ref } from 'vue'
import SitePathRulePanel from './SitePathRulePanel.vue'

const props = defineProps<{
  /// 目标站点 ID（传给 SitePathRulePanel）
  siteId: number
  /// 目标站点名称（用于 header 显示，区分同时存在的多个 slideover）
  siteName: string
}>()

const open = defineModel<boolean>('open', { default: false })

/// 引用嵌入的 SitePathRulePanel，调用其暴露的 save() 与 saving 状态
const panelRef = ref<InstanceType<typeof SitePathRulePanel> | null>(null)

/// footer "保存" 按钮触发：转发给 panel 的 save
/// panel 内部用 saving 状态锁住并发请求；这里 loading 直接绑定 panel.saving
function onSave() {
  panelRef.value?.save()
}
</script>

<template>
  <USlideover
    v-model:open="open"
    :ui="{
      /// 6 列 UTable + 错误提示需要更宽；与 SiteEditSlideover 历史取值一致
      content: 'max-w-7xl',
      header: 'flex items-center justify-between px-6 py-4 border-b border-default',
      body: 'p-6',
      footer: 'flex items-center justify-end gap-3 px-6 py-4 border-t border-default',
    }"
  >
    <template #header>
      <h2 class="text-base font-medium">子路由检测 — {{ props.siteName }}</h2>
      <UButton color="neutral" variant="ghost" icon="i-lucide-x" size="md" square @click="open = false" />
    </template>

    <template #body>
      <SitePathRulePanel ref="panelRef" :site-id="props.siteId" bare />
    </template>

    <template #footer>
      <!--
        仅保留"保存"按钮，放在 slideover 右下角，与 SiteEditSlideover 等保持一致。
        关闭走 header 的 X 按钮或点击 backdrop。
      -->
      <UButton
        label="保存"
        color="primary"
        variant="solid"
        :loading="panelRef?.saving"
        :disabled="panelRef?.saving"
        @click="onSave"
      />
    </template>
  </USlideover>
</template>