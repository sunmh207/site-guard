<script setup lang="ts">
/// 认证页面布局（登录、注册等）。
///
/// - 居中展示内容，背景采用淡渐变与柔和径向高光提升质感
</script>

<template>
  <div class="relative flex min-h-screen items-center justify-center overflow-hidden bg-gradient-to-br from-elevated via-elevated to-success/5 dark:from-inverted dark:via-inverted dark:to-success/10 px-4 py-12">
    <!-- 背景装饰：右上 / 左下柔和光晕 -->
    <div class="pointer-events-none absolute -top-32 -right-32 w-96 h-96 rounded-full bg-success/10 blur-3xl" />
    <div class="pointer-events-none absolute -bottom-32 -left-32 w-96 h-96 rounded-full bg-primary/10 blur-3xl" />

    <div class="relative z-10 w-full flex justify-center">
      <slot />
    </div>
  </div>
</template>