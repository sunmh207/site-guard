<script setup lang="ts">
/// 连续失败阈值设置卡片。
///
/// 单一职责：管理「连续失败 N 次才告警」的全局阈值。
///
/// 状态机：
///   - value === null：未设置（默认 1，提示文案显示「使用默认值」）
///   - value !== null && !editing：只读展示当前值
///   - editing：UFormField + UInput 数字输入 + 保存/取消
///
/// 数据流：
///   - 挂载时调 getConfig；404 → value = null（未设置态）
///   - 保存成功后把 parsed.output 写回 value，并退出编辑态
///   - 取消编辑时把 draft 重置回当前 value，避免脏数据
///
/// 设计取舍：
///   - 不使用 UForm / UFormField submit：阈值只有一个字段，inline 校验更直观
///   - 使用 valibot safeParse 在 save() 里做最终兜底，避免依赖 UI 层
import { ref, computed, onMounted } from 'vue'
import * as v from 'valibot'
import { alertConfirmSettingSchema } from '../schemas/alert-confirm-setting.schema'
import { alertConfirmSettingApi } from '../api/alert-confirm-setting.api'
import type { StatusResult } from '~/shared/types/api'
import type { AlertConfirmConfigResponse } from '../types/alert-confirm-setting.dto'

/// 加载与保存各自用独立 loading 状态，避免互相阻塞 UI。
const loading = ref(false)
const saving = ref(false)

/// value 是后端权威值，draft 是 UI 当前 mid-state（编辑中可能与 value 不一致）。
const value = ref<number | null>(null)
const draft = ref<number>(1)
const editing = ref(false)
const error = ref<string | null>(null)

/// 已设置 = value !== null。
const isSet = computed(() => value.value !== null)
/// 显示值：未设置走 1（默认）。
const displayValue = computed(() => value.value ?? 1)

/// 加载：调 getConfig；404 已经被 api 层 catch → {code:0, data:null}，所以这里
/// 直接走 res.data?.value 取值即可；data 为 null 即未设置态。
async function load() {
  loading.value = true
  error.value = null
  try {
    const res: StatusResult<AlertConfirmConfigResponse | null> = await alertConfirmSettingApi.getConfig()
    const cfg = res.data?.value?.consecutiveFailuresBeforeAlert
    value.value = cfg ?? null
    draft.value = cfg ?? 1
  }
  catch (e: any) {
    error.value = e?.data?.message || e?.message || '加载失败'
  }
  finally {
    loading.value = false
  }
}

/// 进入编辑态：把当前 value 复制到 draft（若未设置，draft 维持 1）。
function startEdit() {
  draft.value = value.value ?? 1
  error.value = null
  editing.value = true
}

/// 取消编辑：重置 draft 到 value，关闭编辑态。
function cancel() {
  draft.value = value.value ?? 1
  error.value = null
  editing.value = false
}

/// 保存：先做 valibot 兜底校验，再调 updateConfig；成功后写回 value。
async function save() {
  const parsed = v.safeParse(alertConfirmSettingSchema, { consecutiveFailuresBeforeAlert: draft.value })
  if (!parsed.success) {
    error.value = parsed.issues[0]?.message ?? '校验失败'
    return
  }
  saving.value = true
  error.value = null
  try {
    await alertConfirmSettingApi.updateConfig({ consecutiveFailuresBeforeAlert: parsed.output.consecutiveFailuresBeforeAlert })
    value.value = parsed.output.consecutiveFailuresBeforeAlert
    editing.value = false
  }
  catch (e: any) {
    error.value = e?.data?.message || e?.message || '保存失败'
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
  cancel,
  startEdit,
  value,
  draft,
  editing,
  loading,
  saving,
  error,
})
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-medium">连续失败阈值</h3>
        <UButton
          v-if="!editing"
          size="sm"
          color="primary"
          variant="outline"
          @click="startEdit"
        >
          {{ isSet ? '编辑' : '设置' }}
        </UButton>
      </div>
    </template>

    <p class="text-sm text-muted max-w-3xl mb-3">
      连续 N 次探测失败才触发告警。默认 1（即单次失败立即告警）。
    </p>

    <!-- 只读区：未设置 / 已设置 -->
    <div v-if="!editing">
      <div class="text-2xl font-semibold">{{ displayValue }}</div>
      <div v-if="!isSet" class="text-xs text-muted mt-1">未设置，使用默认值</div>
    </div>

    <!-- 编辑区 -->
    <div v-else class="space-y-3 max-w-3xl">
      <UFormField label="阈值 N" :error="error">
        <UInput
          v-model.number="draft"
          type="number"
          :min="1"
        />
      </UFormField>
      <div class="flex gap-2">
        <UButton
          color="primary"
          :loading="saving"
          @click="save"
        >
          保存
        </UButton>
        <UButton
          color="neutral"
          variant="outline"
          :disabled="saving"
          @click="cancel"
        >
          取消
        </UButton>
      </div>
    </div>
  </UCard>
</template>
