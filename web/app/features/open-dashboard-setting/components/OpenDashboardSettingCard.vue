<script setup lang="ts">
/// 公开大屏设置卡片组件。
///
/// 单一职责：管理「公开大屏」开关状态。
///
/// 状态机：
///   - loading:onMounted 拉初始值，未结束显示 skeleton
///   - cfg:null → 关闭（默认）；cfg:true → 已开启；UI 上只显示开关 + 当前状态文案
///   - saving:USwitch 切换即刻调 setEnabled，成功后 cfg 同步；失败回滚 UI 状态
///
/// 设计取舍：
///   - 用 USwitch + 文案而不引入表单：单布尔值不需要 UFormField/UInput 包装
///   - 不提供「删除」入口：与「关闭」语义重合，删了实际等价于 false，反而引入歧义
///   - 开启后展示可复制 URL：满足运营侧"开完即贴到大屏机"的诉求；复制按钮用 navigator.clipboard
///     包 try/catch，与 NotificationSettingCard.copyWebhook 同一处理（环境差异兜底）
import { ref, computed, onMounted } from 'vue'
import { useMessage } from '~/shared/composables/useMessage'
import { openDashboardSettingApi } from '~/features/open-dashboard-setting/api/open-dashboard-setting.api'
import { ROUTES } from '~/shared/constants/routes'
import type { StatusResult } from '~/shared/types/api'
import type { ConfigResponse } from '~/features/open-dashboard-setting/types/open-dashboard-setting.dto'

const message = useMessage()

/// 加载与保存各自用独立 loading 状态，避免互相阻塞 UI。
const loading = ref(false)
const saving = ref(false)

/// cfg 与 UI 显示分离：cfg 是后端权威值，display 是 UI 当前 mid-state
/// (在网络往返完成前 USwitch 仍可能已被用户切换)。save 后用 server 返回同步 cfg。
const cfg = ref<boolean | null>(null)
/// USwitch 的 v-model 直接绑 display；切换时 onUpdate:model-value 触发保存。
const display = ref(false)

/// 一旦 cfg 加载成功过，display 必须随之同步，避免 UI 与 DB 漂移。
const isLoaded = computed(() => cfg.value !== null)

/// 服务端配置的已开启状态（DB 没有 key 等同于 false）。
const isEnabled = computed(() => cfg.value === true)

/// 大屏 URL：直接拼 origin + path。import.meta.client 下访问 location 拼出完整地址；
/// SSR 阶段 prefetch 用 useRequestURL 也行，但本卡片仅 admin 后台使用，可以 client-side 兜底。
const dashboardUrl = computed(() => {
  if (!import.meta.client) return ROUTES.OPEN.DASHBOARD
  return `${window.location.origin}${ROUTES.OPEN.DASHBOARD}`
})

/// 404 判定：与 NotificationSettingCard 相同的 fallback 约定。
function isNotFound(e: any): boolean {
  return e?.data?.code === 'NOT_FOUND' || e?.statusCode === 404 || e?.response?.status === 404
}

/// 初始加载：调 getEnabled；404 → 默认关闭；非 404 报错弹 toast。
async function load() {
  loading.value = true
  try {
    const res: StatusResult<ConfigResponse<boolean>> = await openDashboardSettingApi.getEnabled()
    const v = res?.data?.value === true
    cfg.value = v
    display.value = v
  }
  catch (e: any) {
    if (isNotFound(e)) {
      /// DB 无该 key = 默认关闭。这是关键不变量，**不能**把 display 当 true。
      cfg.value = false
      display.value = false
    }
    else {
      message.error(e?.data?.message || e?.message || '加载公开大屏开关失败')
    }
  }
  finally {
    loading.value = false
  }
}

/// 保存：USwitch 切换就触发。乐观更新——失败回滚到 cfg 之前的值。
async function save(next: boolean) {
  const previous = cfg.value ?? false
  /// 立即把 UI 拨到目标值，避免网络往返感知延迟
  display.value = next
  saving.value = true
  try {
    await openDashboardSettingApi.setEnabled(next)
    cfg.value = next
    message.success(next ? '已开启公开大屏' : '已关闭公开大屏')
  }
  catch (e: any) {
    /// 回滚 UI 到原值
    display.value = previous
    cfg.value = previous
    message.error(e?.data?.message || e?.message || '保存失败')
  }
  finally {
    saving.value = false
  }
}

/// 复制大屏 URL 到剪贴板。开启态才有意义；关闭态按钮禁用。
async function copyUrl() {
  if (!isEnabled.value) return
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(dashboardUrl.value)
      message.success('已复制大屏地址')
    }
    else {
      message.warning('当前环境不支持复制，请手动复制：' + dashboardUrl.value)
    }
  }
  catch {
    message.error('复制失败')
  }
}

onMounted(load)

/// 对外暴露：与方法同 id 的 ref 留给测试用。
defineExpose({
  load,
  save,
  copyUrl,
  cfg,
  display,
  loading,
  saving,
})
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-medium">公开大屏</h3>
        <UBadge
          v-if="isLoaded"
          :color="isEnabled ? 'success' : 'neutral'"
          variant="soft"
        >
          {{ isEnabled ? '已开启' : '已关闭' }}
        </UBadge>
      </div>
    </template>

    <div v-if="loading" class="text-sm text-muted">加载中…</div>

    <template v-else>
      <p class="text-sm text-muted max-w-3xl mb-4">
        控制 <code class="font-mono">/open/dashboard</code> 是否对外开放。
        关闭后访问该路径将看到「未开启」提示页，不会返回任何监控数据。
        适用于内网大屏——临时展示后可一键关闭，避免长期暴露在网络上被忽略。
      </p>

      <div class="flex items-center gap-3 max-w-3xl">
        <USwitch
          :model-value="display"
          :disabled="saving"
          @update:model-value="save"
        />
        <span class="text-sm">
          {{ display ? '已开启' : '已关闭' }}
        </span>
        <span v-if="saving" class="text-sm text-muted">保存中…</span>
      </div>

      <!-- 开启态：暴露大屏 URL + 复制按钮，方便运营粘贴到大屏机器 -->
      <div v-if="isEnabled" class="mt-4 flex items-center gap-2 max-w-3xl">
        <UTooltip :text="dashboardUrl" :ui="{ wrapper: 'flex-1 min-w-0' }">
          <UInput
            :model-value="dashboardUrl"
            readonly
            class="w-full"
            :ui="{ base: 'font-mono' }"
            aria-label="公开大屏地址"
          />
        </UTooltip>
        <UButton
          color="neutral"
          variant="outline"
          icon="i-lucide-copy"
          aria-label="复制大屏地址"
          @click="copyUrl"
        >
        </UButton>
      </div>
    </template>
  </UCard>
</template>
