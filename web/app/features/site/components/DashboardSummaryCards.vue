<script setup lang="ts">
import type { DashboardSummary } from '~/features/site/types/site-stats.dto'

defineProps<{
  summary: DashboardSummary
}>()
</script>

<template>
  <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
    <UCard>
      <p class="text-sm text-(--ui-text-muted)">总站点数</p>
      <p class="text-3xl font-semibold mt-1">{{ summary.totalSites }}</p>
    </UCard>
    <UCard>
      <p class="text-sm text-(--ui-text-muted)">健康数</p>
      <p class="text-3xl font-semibold text-(--ui-success) mt-1">{{ summary.healthyCount }}</p>
    </UCard>
    <UCard>
      <p class="text-sm text-(--ui-text-muted)">异常数</p>
      <p
        class="text-3xl font-semibold mt-1"
        :class="summary.abnormalCount > 0 ? 'text-(--ui-error)' : ''"
      >
        {{ summary.abnormalCount }}
      </p>
    </UCard>
    <UCard>
      <p class="text-sm text-(--ui-text-muted)">待检测数</p>
      <p class="text-3xl font-semibold text-(--ui-text-muted) mt-1">{{ summary.pendingCount }}</p>
    </UCard>
    <UCard>
      <p class="text-sm text-(--ui-text-muted)">暂停数</p>
      <p class="text-3xl font-semibold text-(--ui-text-dimmed) mt-1">{{ summary.pausedCount }}</p>
    </UCard>
  </div>
</template>