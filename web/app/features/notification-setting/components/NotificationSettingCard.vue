<script setup lang="ts">
/// 通知设置卡片组件。
///
/// 状态机：
///   - 未配置 + 收起（默认）：仅一行引导 + 「立即设置」 / 标题右侧「设置」按钮
///   - 未配置 + 展开：渲染表单 + 「取消」「测试连接」「保存」
///   - 已配置 + 只读：只读展示 + 「已设置」徽标 + 「删除」「编辑」
///   - 已配置 + 编辑中：表单预填 + 「取消」「测试连接」「保存」
///
/// 设计取舍：
///   - 未配置态默认收起：通知 Tab 还要放阈值卡片，垂直空间宝贵；
///     让首次访问的用户主动点「设置」再展开表单，比直接灌一片空字段更克制
///   - 已配置态不收起：用户已配置后，频繁查看/复制 webhook 是主要诉求，保持只读展开
///
/// 数据流：
///   - 挂载时调 getConfig；404 → cfg = null（未配置 + 收起态）
///   - 保存成功后 cfg 变成非 null，自动从「未配置 + 展开」切到「已配置 + 只读」
///   - 删除成功后 cfg 变 null，回到「未配置 + 收起」
import { ref, computed, onMounted } from 'vue'
import { useMessage } from '~/shared/composables/useMessage'
import { notificationSettingApi } from '~/features/notification-setting/api/notification-setting.api'
import { maskWebhook } from '~/shared/utils/mask'
import type { StatusResult } from '~/shared/types/api'
import type {
  ConfigResponse,
  NotificationConfig,
} from '~/features/notification-setting/types/notification-setting.dto'
import {
  ROBOT_PLATFORM_OPTIONS,
  PLATFORM_LABEL_MAP,
} from '~/features/notification-setting/constants/notification-setting.constants'

/// 初始化一个空表单（用于"未设置态"或"编辑态预填"）。
/// secret 默认空串：编辑留空 → 后端 Merger 沿用旧值（Stage B4 NotificationConfigMerger）。
function emptyForm(): NotificationConfig {
  return {
    enabled: false,
    platform: 'DINGTALK',
    webhookUrl: '',
    secret: '',
  }
}

const message = useMessage()

/// 加载 / 保存 / 删除 / 测试 / 复制 这几个子流程各自持有 loading，
/// 避免一个 ref 同时控制多个按钮导致状态混乱。
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const testing = ref(false)

const editing = ref(false)
const cfg = ref<NotificationConfig | null>(null)
const form = ref<NotificationConfig>(emptyForm())

/// 已配置态 = 后端有值；编辑态 = 已配置 + editing 标记。
const isConfigured = computed(() => cfg.value !== null)
const isEditing = computed(() => editing.value && isConfigured.value)
/// 未配置态用 expanded 控制表单是否展开；与已配置态的 editing 完全独立。
/// 两者永远不会同时为 true（cfg 不为空时 expanded 永远没意义）。
const expanded = ref(false)
/// 表单区显示：未配置态需先展开；已配置态走编辑态。
const showForm = computed(() => {
  if (isConfigured.value) return editing.value
  return expanded.value
})

/// 404 判定：后端没有这行配置。组件把它当作"未设置"。
/// 非 404 的错误直接弹 toast。
function isNotFound(e: any): boolean {
  return e?.data?.code === 'NOT_FOUND' || e?.statusCode === 404 || e?.response?.status === 404
}

/// 展开 / 收起未配置态表单。collapseForm 时清空 form，避免下次展开残留输入。
function expandForm() {
  expanded.value = true
}
function collapseForm() {
  expanded.value = false
  form.value = emptyForm()
}

/// 拉取配置；404 → 切到未配置态。
async function load() {
  loading.value = true
  try {
    /// 后端把响应包在 StatusResult（{ code, data, message }）里；这里走
    /// res.data.value 取真正的 ConfigResponse。直接读 res.value 会拿到
    /// undefined，导致 cfg / form 被写成空，刷新后页面看到空 Webhook。
    const res: StatusResult<ConfigResponse<NotificationConfig>> = await notificationSettingApi.getConfig()
    cfg.value = res.data.value
    /// 同步一份到 form，保持打开表单时是当前最新值。
    form.value = { ...emptyForm(), ...res.data.value }
  }
  catch (e: any) {
    if (isNotFound(e)) {
      cfg.value = null
      form.value = emptyForm()
    }
    else {
      message.error(e?.data?.message || e?.message || '加载失败')
    }
  }
  finally {
    loading.value = false
  }
}

/// 切到编辑态；先把当前 cfg 复制到 form，避免双向绑定直接动 cfg。
function startEdit() {
  if (!cfg.value) return
  form.value = { ...emptyForm(), ...cfg.value }
  editing.value = true
}

/// 退出编辑态（不清空 form，下次进编辑态会被 startEdit 重置）。
function cancelEdit() {
  editing.value = false
}

/// 兜底校验：UI 层 UForm 一般已在 valibot 校验过，但保存也可能被外部直接调用。
function validate(): string | null {
  if (!form.value.webhookUrl?.trim()) return '请输入 Webhook URL'
  if (!/^https?:\/\/.+/.test(form.value.webhookUrl.trim())) return 'Webhook URL 必须以 http:// 或 https:// 开头'
  return null
}

async function save() {
  const err = validate()
  if (err) { message.error(err); return }
  saving.value = true
  try {
    await notificationSettingApi.updateConfig({
      enabled: !!form.value.enabled,
      platform: form.value.platform,
      webhookUrl: form.value.webhookUrl.trim(),
      secret: form.value.secret?.trim() || undefined,
    })
    /// 保存成功后 reload，确保 cfg / form 拿到后端权威值。
    await load()
    editing.value = false
    /// 未配置态首次保存 → cfg 变非 null → 自动切到「已配置 + 只读」；收起标志归零。
    expanded.value = false
    message.success('通知设置已保存')
  }
  catch (e: any) {
    message.error(e?.data?.message || e?.message || '保存失败')
  }
  finally {
    saving.value = false
  }
}

async function remove() {
  deleting.value = true
  try {
    await notificationSettingApi.deleteConfig()
    cfg.value = null
    form.value = emptyForm()
    editing.value = false
    /// 删除后回到「未配置 + 收起」：引导提示与「设置」按钮重新出现。
    expanded.value = false
    message.success('已删除通知配置')
  }
  catch (e: any) {
    message.error(e?.data?.message || e?.message || '删除失败')
  }
  finally {
    deleting.value = false
  }
}

/// 测试连接：始终用 form 当前值（无论是否已配置均可触发）。
async function testConnection() {
  if (!form.value.webhookUrl?.trim()) {
    message.error('请先输入 Webhook URL')
    return
  }
  testing.value = true
  try {
    const res = await notificationSettingApi.testWebhook({
      platform: form.value.platform,
      webhookUrl: form.value.webhookUrl.trim(),
      secret: form.value.secret?.trim() || undefined,
    })
    if (res.data.success) {
      message.success(res.data.message || '连接测试成功')
    }
    else {
      message.error(res.data.message || '连接测试失败')
    }
  }
  catch (e: any) {
    message.error(e?.data?.message || e?.message || '连接测试失败')
  }
  finally {
    testing.value = false
  }
}

/// 复制完整 webhook URL 到剪贴板（只读态的"复制"按钮）。
/// clipboard API 在测试环境不存在 → 走 try/catch 兜底。
async function copyWebhook() {
  if (!cfg.value?.webhookUrl) return
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(cfg.value.webhookUrl)
      message.success('已复制')
    }
    else {
      message.warning('当前环境不支持复制')
    }
  }
  catch {
    message.error('复制失败')
  }
}

onMounted(load)

/// 对外暴露：方法和 ref 都要挂上去，便于 wrapper.vm 直接调。
defineExpose({
  startEdit,
  cancelEdit,
  save,
  remove,
  load,
  testConnection,
  copyWebhook,
  expandForm,
  collapseForm,
  cfg,
  form,
  editing,
  loading,
  saving,
  deleting,
  testing,
  expanded,
})
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-medium">通知设置</h3>
        <div class="flex items-center gap-2">
          <UBadge v-if="isConfigured && !editing" color="success" variant="soft">已设置</UBadge>
          <UButton
            v-if="!isConfigured && !expanded"
            size="sm"
            color="primary"
            variant="outline"
            data-testid="notification-setup-btn"
            @click="expandForm"
          >
            设置
          </UButton>
          <UButton
            v-if="!isConfigured && expanded"
            size="sm"
            color="neutral"
            variant="outline"
            @click="collapseForm"
          >
            取消
          </UButton>
        </div>
      </div>
    </template>

    <!-- 未配置态引导：仅在「未配置 + 收起」时显示，给首次访问的用户一个视觉锚点。
         点击「立即设置」展开表单；不再用 toast 提示「配置不存在」。 -->
    <div
      v-if="!isConfigured && !expanded"
      class="text-sm text-muted max-w-3xl"
      data-testid="notification-empty-hint"
    >
      未配置通知通道。探测到异常时将无法发送告警。
      <button
        type="button"
        class="text-primary underline ml-1"
        data-testid="notification-setup-link"
        @click="expandForm"
      >
        立即设置
      </button>
    </div>

    <!-- 表单区：未配置态（需先展开）+ 已配置编辑态 -->
    <div v-if="showForm">
      <h4 v-if="isEditing" class="text-sm font-medium mb-3">编辑通知</h4>
      <h4 v-else class="text-sm font-medium mb-3">设置通知</h4>

      <div class="space-y-3 max-w-3xl">
        <UFormField label="平台" name="platform" :ui="{ label: 'text-sm font-normal mb-1' }">
          <USelect
            v-model="form.platform"
            :items="ROBOT_PLATFORM_OPTIONS"
            value-key="value"
            size="md"
            class="w-32"
          />
        </UFormField>

        <UFormField label="Webhook URL" name="webhookUrl" :ui="{ label: 'text-sm font-normal mb-1' }">
          <UInput
            v-model="form.webhookUrl"
            placeholder="https://..."
            size="md"
            class="w-full"
          />
        </UFormField>

        <UFormField
          label="签名密钥"
          name="secret"
          help="可选；钉钉/飞书加签用；编辑留空表示保持不变"
          :ui="{ label: 'text-sm font-normal mb-1' }"
        >
          <UInput
            v-model="form.secret"
            type="password"
            placeholder="留空表示保持不变"
            size="md"
            class="w-96"
          />
        </UFormField>

        <UFormField label="启用" name="enabled" :ui="{ label: 'text-sm font-normal mb-1' }">
          <USwitch v-model="form.enabled" />
        </UFormField>
      </div>

      <div class="mt-4 flex gap-2 justify-end">
        <UButton
          v-if="isEditing"
          color="neutral"
          variant="outline"
          :loading="saving"
          @click="cancelEdit"
        >
          取消
        </UButton>
        <UButton
          color="neutral"
          variant="outline"
          :loading="testing"
          :disabled="!form.webhookUrl"
          @click="testConnection"
        >
          测试连接
        </UButton>
        <UButton
          color="primary"
          :loading="saving"
          @click="save"
        >
          保存
        </UButton>
      </div>
    </div>

    <!-- 只读区：已配置态 -->
    <div v-else-if="isConfigured">
      <dl class="space-y-3 max-w-3xl">
        <div class="flex">
          <dt class="w-32 text-sm text-muted">平台</dt>
          <dd class="text-sm">{{ PLATFORM_LABEL_MAP[cfg!.platform] }}</dd>
        </div>
        <div class="flex items-center">
          <dt class="w-32 text-sm text-muted">Webhook</dt>
          <dd class="text-sm flex items-center">
            <UTooltip :text="cfg!.webhookUrl">
              <span class="font-mono">{{ maskWebhook(cfg!.webhookUrl) }}</span>
            </UTooltip>
            <UButton
              size="xs"
              color="neutral"
              variant="ghost"
              icon="i-lucide-copy"
              class="ml-2"
              aria-label="复制 Webhook"
              @click="copyWebhook"
            />
          </dd>
        </div>
      </dl>

      <div class="mt-4 flex items-center justify-between">
        <div class="flex items-center gap-2">
          <USwitch :model-value="!!cfg!.enabled" disabled />
          <span class="text-sm text-muted">{{ cfg!.enabled ? '已启用' : '未启用' }}</span>
        </div>
        <div class="flex gap-2">
          <Popconfirm
            title="确认删除"
            description="确定要删除通知配置吗？"
            color="error"
            content="{ align: 'start', side: 'left' }"
            @ok="remove"
          >
            <UButton color="error" variant="outline" :loading="deleting">
              删除
            </UButton>
          </Popconfirm>
          <UButton color="primary" @click="startEdit">
            编辑
          </UButton>
        </div>
      </div>
    </div>
  </UCard>
</template>
