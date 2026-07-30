<script setup lang="ts">
import type { SitePathRuleTestResultDto } from '../types/site-path-rule.dto'

defineProps<{ result: SitePathRuleTestResultDto }>()
</script>

<template>
  <div class="space-y-3 rounded-lg border border-(--ui-border) p-4">
    <div class="flex items-center gap-2">
      <UBadge :color="result.healthy ? 'success' : 'error'" variant="subtle">
        {{ result.healthy ? '测试正常' : '测试异常' }}
      </UBadge>
      <span class="text-sm">HTTP {{ result.httpStatus ?? '—' }}</span>
      <UBadge :color="result.httpStatusMatched ? 'success' : 'error'" variant="subtle">
        {{ result.httpStatusMatched ? '状态码符合' : '状态码不符' }}
      </UBadge>
    </div>
    <p class="text-sm">{{ result.summary }}</p>

    <div v-if="result.conditions.length" class="space-y-2">
      <div
        v-for="condition in result.conditions"
        :key="condition.index"
        class="rounded-md bg-(--ui-bg-muted) p-3 text-sm"
      >
        <div class="flex items-center gap-2">
          <UBadge :color="condition.matched ? 'success' : 'error'" variant="subtle">
            {{ condition.matched ? '满足' : '不满足' }}
          </UBadge>
          <code>{{ condition.path }}</code>
          <span>{{ condition.operator }}</span>
        </div>
        <div class="mt-1 text-(--ui-text-muted)">
          实际值：{{ condition.actualValue ?? '字段不存在' }}；{{ condition.reason }}
        </div>
      </div>
    </div>
  </div>
</template>
