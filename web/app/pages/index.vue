<template>
  <div class="min-h-screen flex items-center justify-center bg-default">
    <div class="text-center">
      <UIcon name="i-lucide-loader-circle" class="w-12 h-12 text-primary animate-spin mx-auto mb-4"/>
      <p class="text-muted">正在跳转...</p>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({
  layout: false,
})

const auth = useAuth()
const router = useRouter()

onMounted(() => {
  // 检查登录状态
  if (auth.isAuthenticated) {
    // 已登录，跳转到管理后台首页
    router.push('/admin/dashboard')
  } else {
    // 未登录，跳转到登录页
    router.push('/login')
  }
})
</script>
