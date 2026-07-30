<script setup lang="ts">
/// 关于页卡片（只读）。
///
/// 展示当前站点品牌与 Site Guard 的固定产品信息、技术栈。
const { data: branding } = useBranding()

const items = computed(() => [
  { label: '名称', value: branding.value.name },
  { label: '简介', value: '站点监控告警系统' },
  { label: '前端', value: 'Nuxt 4 (Vue 3 + TypeScript) + @nuxt/ui' },
  { label: '后端', value: 'Spring Boot 4.0 (Java 25) + Spring Data JPA + Spring Security' },
  { label: '数据库', value: 'MySQL 8' },
])
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex min-w-0 items-center gap-2">
        <BrandIcon
          :src="branding.iconUrl"
          :custom="branding.customIcon"
          :alt="`${branding.name} 图标`"
          class="h-6 w-6 flex-shrink-0 text-success"
        />
        <h3 class="min-w-0 truncate text-lg font-medium" :title="`关于 ${branding.name}`">
          关于 {{ branding.name }}
        </h3>
      </div>
    </template>

    <dl class="space-y-3 max-w-3xl">
      <div v-for="item in items" :key="item.label" class="flex">
        <dt class="w-24 text-sm text-muted">{{ item.label }}</dt>
        <dd class="text-sm">{{ item.value }}</dd>
      </div>
    </dl>
  </UCard>
</template>
