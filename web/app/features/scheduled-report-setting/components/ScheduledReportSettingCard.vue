<script setup lang="ts">
/// 定时发送报告设置卡片组件。
///
/// 单一职责：管理「定时报告」开关与每日发送时刻。
///
/// 状态机：
///   - loading: onMounted 拉初始值，未结束显示 skeleton
///   - cfg: null 未配置（默认关闭） → USwitch off；有值按 enabled/time 回显
///   - saving: 开关/时间变更即刻调 setConfig，成功后 cfg 同步；失败回滚
///
/// 设计取舍：
///   - USwitch + 时间选择器，单 BOOLEAN + 单 time 值，无需表单包装
///   - 未配置时默认收起开关区域，保持与 NotificationSettingCard 类似克制风格
///   - 启用前提：通知机器人已配置；未配置时给出提示
import { ref, computed, onMounted } from 'vue'
import { useMessage } from '~/shared/composables/useMessage'
import { scheduledReportSettingApi } from '~/features/scheduled-report-setting/api/scheduled-report-setting.api'
import type { StatusResult, ConfigResponse } from '~/shared/types/api'
import type { ScheduledReportConfig } from '~/features/scheduled-report-setting/types/scheduled-report.dto'

const message = useMessage()

/// 加载与保存各自用独立 loading 状态，避免互相阻塞 UI。
const loading = ref(false)
const saving = ref(false)

/// cfg 与 UI 显示分离：cfg 是后端权威值，display 是 UI 当前 mid-state。
const cfg = ref<ScheduledReportConfig | null>(null)
const displayEnabled = ref(false)
const displayTime = ref('08:00')

/// 一旦 cfg 加载成功过，UI 随之同步。
const isLoaded = computed(() => cfg.value !== null)
const isEnabled = computed(() => cfg.value?.enabled === true)

/// 404 判定：与其它设置卡片相同的 fallback 约定。
function isNotFound(e: any): boolean {
  return e?.data?.code === 'NOT_FOUND' || e?.statusCode === 404 || e?.response?.status === 404
}

/// 初始加载：调 getConfig；404 → 默认关闭 + 默认时间。
async function load() {
  loading.value = true
  try {
    const res: StatusResult<ConfigResponse<ScheduledReportConfig> | null> = await scheduledReportSettingApi.getConfig()
    const v = res?.data?.value ?? null
    cfg.value = v
    displayEnabled.value = v?.enabled === true
    displayTime.value = v?.time ?? '08:00'
  }
  catch (e: any) {
    if (isNotFound(e)) {
      /// DB 无该 key = 默认关闭。
      cfg.value = null
      displayEnabled.value = false
      displayTime.value = '08:00'
    }
    else {
      message.error(e?.data?.message || e?.message || '加载定时报告配置失败')
    }
  }
  finally {
    loading.value = false
  }
}

/// 保存：开关切换或时间变更都触发。乐观更新——失败回滚到之前的值。
/// enabled 与 time 整体提交，保持原子性。
async function save(patch: Partial<ScheduledReportConfig>) {
  const previous = { enabled: displayEnabled.value, time: displayTime.value }
  if ('enabled' in patch) displayEnabled.value = !!patch.enabled
  if ('time' in patch) displayTime.value = patch.time ?? '08:00'
  saving.value = true
  try {
    const res = await scheduledReportSettingApi.setConfig({
      enabled: displayEnabled.value,
      time: displayTime.value,
    })
    cfg.value = res.data.value
    message.success(displayEnabled.value ? '已开启定时报告' : '已关闭定时报告')
  }
  catch (e: any) {
    /// 回滚 UI 到原值
    displayEnabled.value = previous.enabled
    displayTime.value = previous.time
    message.error(e?.data?.message || e?.message || '保存失败')
  }
  finally {
    saving.value = false
  }
}

onMounted(load)

/// 对外暴露：与方法同 id 的 ref 留给测试用。
defineExpose({
  load,
  save,
  cfg,
  displayEnabled,
  displayTime,
  loading,
  saving,
})
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-medium">定时报告</h3>
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
        每日定时将仪表盘的摘要与异常列表推送到已配置的通知机器人。
        需在「通知」中先配置并启用机器人，报告才会正常发送。
      </p>

      <div class="flex items-center gap-3 max-w-3xl">
        <USwitch
          :model-value="displayEnabled"
          :disabled="saving"
          @update:model-value="(v) => save({ enabled: v })"
        />
        <span class="text-sm">定时报告</span>
        <span v-if="saving" class="text-sm text-muted">保存中…</span>
      </div>

      <!-- 启用后展示发送时刻选择 -->
      <div v-if="displayEnabled" class="mt-4 flex items-center gap-3 max-w-3xl">
        <span class="text-sm">每日发送时刻</span>
        <UInput
          type="time"
          :model-value="displayTime"
          :disabled="saving"
          @update:model-value="(v: string) => save({ time: v })"
          aria-label="每日发送时刻"
        />
      </div>
    </template>
  </UCard>
</template>
