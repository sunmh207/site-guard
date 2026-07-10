<script setup lang="ts">
/// 通用设置页。
///
/// 顶部 Tab 切换（「通知」「显示」「关于」）。
/// Tab 状态通过 query 参数同步，支持深链（/admin/settings?tab=about）。
///
/// 「通知」Tab 承载告警相关设置：通知通道（NotificationSettingCard） +
/// 连续失败阈值（AlertConfirmSettingCard）。
///
/// 「显示」Tab 承载非通知类 UI 开关：目前只有公开大屏开关，
/// 未来如出现"暗色/亮色默认"等同类开关，同收口到此 Tab。
import { computed } from 'vue'
import NotificationSettingCard from '~/features/notification-setting/components/NotificationSettingCard.vue'
import OpenDashboardSettingCard from '~/features/open-dashboard-setting/components/OpenDashboardSettingCard.vue'
import AlertConfirmSettingCard from '~/features/alert-confirm-setting/components/AlertConfirmSettingCard.vue'
import AboutCard from '~/features/about/components/AboutCard.vue'

definePageMeta({
  layout: 'admin',
})

const route = useRoute()
const router = useRouter()

type Tab = 'notification' | 'display' | 'about'

const activeTab = computed<Tab>({
  get() {
    const t = route.query.tab
    if (t === 'display') return 'display'
    if (t === 'about') return 'about'
    return 'notification'
  },
  set(v: Tab) {
    router.replace({ query: { ...route.query, tab: v } })
  },
})

const tabItems = [
  { label: '通知', value: 'notification' as Tab, slot: 'notification' },
  { label: '显示', value: 'display' as Tab, slot: 'display' },
  { label: '关于', value: 'about' as Tab, slot: 'about' },
]
</script>

<template>
  <UDashboardPanel id="settings">
    <template #header>
      <UDashboardNavbar title="设置">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
    </template>
    <template #body>
      <UTabs v-model="activeTab" :items="tabItems">
        <template #notification>
          <NotificationSettingCard />
          <div class="mt-4">
            <AlertConfirmSettingCard />
          </div>
        </template>
        <template #display>
          <OpenDashboardSettingCard />
        </template>
        <template #about>
          <AboutCard />
        </template>
      </UTabs>
    </template>
  </UDashboardPanel>
</template>
