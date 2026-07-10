<script setup lang="ts">
/// 关于页卡片（只读）。
///
/// 展示 Site Guard 的基本信息：名称、简介、技术栈。
/// 信息在组件内硬编码，暂不暴露配置。
const items = [
  { label: '名称', value: 'Site Guard' },
  { label: '简介', value: '站点监控告警系统' },
  { label: '前端', value: 'Nuxt 4 (Vue 3 + TypeScript) + @nuxt/ui' },
  { label: '后端', value: 'Spring Boot 4.0 (Java 25) + Spring Data JPA + Spring Security' },
  { label: '数据库', value: 'MySQL 8' },
]
</script>

<template>
  <UCard>
    <template #header>
      <h3 class="text-lg font-medium">关于 Site Guard</h3>
    </template>

    <dl class="space-y-3 max-w-3xl">
      <div v-for="item in items" :key="item.label" class="flex">
        <dt class="w-24 text-sm text-muted">{{ item.label }}</dt>
        <dd class="text-sm">{{ item.value }}</dd>
      </div>
    </dl>
  </UCard>
</template>
