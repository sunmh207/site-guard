<script setup lang="ts">
/// 公开大屏布局。
///
/// - 不强制暗色：跟随用户在 AdminUserMenu.vue「外观」菜单里设置的 colorMode.preference
///   （前一份 spec 曾因"避免大屏半夜突然变亮"硬写 dark；本次按用户反馈解锁）
/// - 无侧边栏、无登录 UI、无用户菜单
/// - 顶部 navbar 仅承载标题与「最后刷新」时间
</script>

<template>
  <div class="min-h-screen bg-(--ui-bg) text-(--ui-text)">
    <slot />
  </div>
</template>