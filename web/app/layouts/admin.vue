<script setup lang="ts">
/// 管理后台布局。
///
/// - 左侧侧边栏显示所有管理后台菜单项
/// - 移动端自动折叠侧边栏
import type { NavigationMenuItem } from '@nuxt/ui'
import { ROUTES } from '~/shared/constants/routes'

const route = useRoute()
const open = ref(true) // 默认展开

// 菜单项配置：每个后台模块对应一项
const items: NavigationMenuItem[] = [
  {
    label: '仪表盘',
    icon: 'i-lucide-gauge',
    to: ROUTES.ADMIN.ADMIN_DASHBOARD,
    onSelect: () => {
      if (import.meta.client && window.innerWidth < 1024) {
        open.value = false
      }
    },
  },
  {
    label: '站点管理',
    icon: 'i-lucide-globe',
    to: '/admin/sites',
    onSelect: () => {
      if (import.meta.client && window.innerWidth < 1024) {
        open.value = false
      }
    },
  },
  {
    label: '设置',
    icon: 'i-lucide-settings',
    to: ROUTES.ADMIN.SETTINGS,
    onSelect: () => {
      if (import.meta.client && window.innerWidth < 1024) {
        open.value = false
      }
    },
  }
]

// 监听路由变化，移动端自动关闭侧边栏
watch(() => route.path, () => {
  if (import.meta.client && window.innerWidth < 1024) {
    open.value = false
  }
})
</script>

<template>
  <UDashboardGroup unit="rem">
    <UDashboardSidebar v-model:open="open" collapsible resizable>
      <template #header="{ collapsed }">
        <div class="flex justify-between items-center px-0.5 py-1.5 w-full">
          <div class="flex items-center gap-2 min-w-0">
            <UIcon 
              name="i-lucide-radar"
              class="w-6 h-6 text-success flex-shrink-0"
            />
            <Transition
              enter-active-class="transition-opacity duration-200"
              leave-active-class="transition-opacity duration-150"
              enter-from-class="opacity-0"
              leave-to-class="opacity-0"
            >
              <span 
                v-show="!collapsed" 
                class="text-lg font-medium text-highlighted  whitespace-nowrap"
              >
                Site Guard
              </span>
            </Transition>
          </div>
        </div>
      </template>

      <template #default="{ collapsed }">
        <UNavigationMenu :collapsed="collapsed" tooltip :items="items" orientation="vertical" />
      </template>

      <template #footer="{ collapsed }">
        <AdminUserMenu :collapsed="collapsed" />
      </template>
    </UDashboardSidebar>

    <slot />
  </UDashboardGroup>
</template>

