<script setup lang="ts">
const toaster = { position: 'top-center', progress: false, close: false } as const
</script>

<template>
  <UApp :toaster="toaster">
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
  </UApp>
</template>
