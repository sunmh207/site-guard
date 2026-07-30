<script setup lang="ts">
const toaster = { position: 'top-center', progress: false, close: false } as const
const { data: branding, ensureLoaded } = useBranding()

/// 应用根组件只触发一次公开读取；composable 自身仍负责跨调用方并发去重。
void ensureLoaded()

/// 使用稳定 key 更新同一个 favicon link，避免品牌切换后 head 中累积旧图标。
useHead(() => ({
  title: branding.value.name,
  link: [
    {
      key: 'branding-favicon',
      rel: 'icon',
      href: branding.value.iconUrl,
    },
  ],
}))
</script>

<template>
  <UApp :toaster="toaster">
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
  </UApp>
</template>
