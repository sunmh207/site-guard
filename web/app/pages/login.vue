<script setup lang="ts">
/// 登录页面。
///
/// - 展示站点品牌 Site Guard，并提供用户名/密码登录入口
/// - 支持"记住我"自动填充上次登录的用户名
import * as v from 'valibot'
import { useAuth } from '~/features/auth/composables/useAuth'
import { ROUTES } from '~/shared/constants/routes'

definePageMeta({
  layout: 'auth',
})

const auth = useAuth()
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const showPassword = ref(false)

const schema = v.object({
  username: v.pipe(
      v.string(),
      v.nonEmpty('不能为空'),
      v.minLength(2, '必须大于 2 个字符'),
      v.maxLength(32, '必须小于 32 个字符'),
  ),
  password: v.pipe(
      v.string(),
      v.nonEmpty('不能为空'),
      v.minLength(2, '必须大于 2 个字符'),
      v.maxLength(32, '必须小于 32 个字符'),
  ),
})

type Schema = v.InferOutput<typeof schema>

const state = reactive<Partial<Schema>>({
  username: '',
  password: '',
  rememberMe: false,
})

// 如果已登录，重定向到看板页面
onMounted(() => {
  if (import.meta.client && auth.isAuthenticated.value) {
    router.push(ROUTES.ADMIN.DASHBOARD)
  }

  // 从 localStorage 读取记住的用户名
  if (import.meta.client) {
    const rememberedUsername = localStorage.getItem('rememberedUsername')
    if (rememberedUsername) {
      state.username = rememberedUsername
      state.rememberMe = true
    }
  }
})

const handleLogin = async () => {
  errorMessage.value = ''
  loading.value = true

  try {
    await auth.login({
      username: state.username,
      password: state.password,
    })

    // 处理"记住我"功能
    if (import.meta.client) {
      if (state.rememberMe) {
        localStorage.setItem('rememberedUsername', state.username)
      } else {
        localStorage.removeItem('rememberedUsername')
      }
    }

    await router.push(ROUTES.ADMIN.DASHBOARD)
  } catch (error: any) {
    errorMessage.value = error.message || '登录失败，请检查用户名和密码'
  } finally {
    loading.value = false
  }
}
</script>


<template>
  <div class="w-full max-w-[28rem]">
    <!-- 品牌区域 -->
    <div class="flex flex-col items-center mb-8">
      <div class="flex items-center gap-3 mb-2">
        <div class="flex items-center justify-center w-12 h-12 rounded-xl bg-success/10 ring-1 ring-success/20">
          <UIcon name="i-lucide-radar" class="w-7 h-7 text-success"/>
        </div>
        <span class="text-3xl font-semibold text-highlighted tracking-tight">Site Guard</span>
      </div>
      <p class="mt-3 text-lg font-semibold text-highlighted tracking-wide">
        不让故障,悄悄发生
      </p>
    </div>

    <UCard
        class="shadow-xl ring-1 ring-default backdrop-blur-sm"
        :ui="{
          root: 'bg-default/95',
          body: 'px-8 sm:px-10 py-8 sm:py-10',
        }"
    >
      <UForm :schema="schema" :state="state" class="space-y-5" data-id="login-form" @submit="handleLogin">
        <UFormField label="用户名" name="username" required size="lg">
          <UInput
              v-model="state.username"
              class="w-full"
              size="lg"
              placeholder="请输入用户名"
              :ui="{ base: 'w-full' }"
          >
            <template #leading>
              <UIcon name="i-lucide-user" class="w-4 h-4 text-muted"/>
            </template>
          </UInput>
        </UFormField>

        <UFormField label="密码" name="password" required size="lg">
          <UInput
              v-model="state.password"
              :type="showPassword ? 'text' : 'password'"
              class="w-full"
              size="lg"
              placeholder="请输入密码"
              :ui="{ base: 'w-full' }"
          >
            <template #leading>
              <UIcon name="i-lucide-lock" class="w-4 h-4 text-muted"/>
            </template>
            <template #trailing>
              <UButton
                  color="neutral"
                  variant="link"
                  size="sm"
                  :icon="showPassword ? 'i-lucide-eye-off' : 'i-lucide-eye'"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
              />
            </template>
          </UInput>
        </UFormField>

        <!-- 记住我 -->
        <div class="flex items-center justify-between pt-1">
          <UCheckbox
              v-model="state.rememberMe"
              label="记住我"
              :disabled="loading"
          />
        </div>

        <!-- 错误提示 -->
        <UAlert
            v-if="errorMessage"
            color="error"
            variant="subtle"
            :title="errorMessage"
            :close-button="{ icon: 'i-lucide-x', color: 'gray', variant: 'link' }"
            @close="errorMessage = ''"
        />

        <UButton type="submit" block size="lg" :loading="loading" class="mt-2">
          <template #leading>
            <UIcon name="i-lucide-log-in" class="w-4 h-4"/>
          </template>
          登录
        </UButton>
      </UForm>
    </UCard>
  </div>

</template>