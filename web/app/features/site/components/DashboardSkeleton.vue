<script setup lang="ts">
/// Dashboard 首屏加载骨架。
///
/// 复刻 DashboardSummaryCards（5 张 UCard 横排）+ RecentAlertsTable（1 张 UCard + UTable 多行）
/// 的真实布局，用于 data 还没到达时的占位；加载完成切回真实组件，避免布局跳动。
///
/// 组件本身不感知 useFetch 状态——调用方负责 v-if="!data" 切换。
/// 主题跟随项目 colorMode（通过 Nuxt UI 的 UCard/USkeleton 自动适配），不硬写 dark/light。
const props = withDefaults(defineProps<{
  /// 表格区域占位行数。clamp 到 [5, 10]：再多视觉上只是"还在加载"，没差别；
  /// 5 是常见空载行数，与 RecentAlertsTable 默认体验对齐。
  rows?: number
}>(), { rows: 5 })

/// rows clamp 到 [5, 10]：调用方传 1 → 5，传 20 → 10。
const clampedRows = computed(() => Math.min(10, Math.max(5, props.rows)))
</script>

<template>
  <div class="bg-(--ui-bg)">
    <!-- 5 张摘要卡片占位：UCard 壳 + 标题骨架 + 大数字骨架 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
      <UCard v-for="i in 5" :key="i">
        <USkeleton class="h-3 w-16" />
        <USkeleton class="h-8 w-20 mt-2" />
      </UCard>
    </div>

    <!-- 告警表占位：UCard 壳 + 标题骨架 + N 行表格骨架 -->
    <UCard class="mt-4">
      <template #header>
        <USkeleton class="h-4 w-12" />
      </template>
      <div class="space-y-2">
        <div v-for="i in clampedRows" :key="i" class="flex gap-4">
          <USkeleton class="h-4 w-24" />
          <USkeleton class="h-4 w-16" />
          <USkeleton class="h-4 flex-1" />
          <USkeleton class="h-4 w-20" />
        </div>
      </div>
    </UCard>
  </div>
</template>
