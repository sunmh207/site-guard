<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { brandingApi } from '../api/branding.api'
import { DEFAULT_BRANDING, type BrandingDto } from '../types/branding.dto'
import { useMessage } from '~/shared/composables/useMessage'

const MAX_ICON_SIZE = 2 * 1024 * 1024
const ALLOWED_ICON_TYPES = new Set(['image/png', 'image/jpeg'])

const message = useMessage()
const { apply } = useBranding()
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const name = ref(DEFAULT_BRANDING.name)
const current = ref<BrandingDto>({ ...DEFAULT_BRANDING })
const iconFile = ref<File | null>(null)
const previewUrl = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

function errorText(error: any, fallback: string): string {
  return error?.data?.message || error?.response?._data?.message || error?.message || fallback
}

function revokePreview() {
  if (!previewUrl.value) return
  URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = null
}

/// 替换选择文件前先回收上一个 blob URL，避免管理员反复试图标时持续占用内存。
function setIconFile(file: File | null) {
  revokePreview()
  iconFile.value = file
  if (file) previewUrl.value = URL.createObjectURL(file)
}

function clearFileInput() {
  setIconFile(null)
  if (fileInput.value) fileInput.value.value = ''
}

function onIconChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  if (!file) {
    clearFileInput()
    return
  }
  if (!ALLOWED_ICON_TYPES.has(file.type)) {
    message.error('图标仅支持 PNG 或 JPEG 格式')
    clearFileInput()
    return
  }
  if (file.size > MAX_ICON_SIZE) {
    message.error('图标大小不能超过 2 MiB')
    clearFileInput()
    return
  }
  setIconFile(file)
}

async function load() {
  loading.value = true
  try {
    const res = await brandingApi.getAdmin()
    current.value = res.data
    name.value = res.data.name
  }
  catch (error: any) {
    message.error(errorText(error, '加载品牌设置失败'))
  }
  finally {
    loading.value = false
  }
}

async function save() {
  const normalizedName = name.value.trim()
  if (!normalizedName) {
    message.error('请输入站点名称')
    return
  }

  saving.value = true
  try {
    const formData = new FormData()
    formData.append('siteName', normalizedName)
    if (iconFile.value) formData.append('icon', iconFile.value)

    const res = await brandingApi.set(formData)
    /// 必须读取 StatusResult.data；apply 管理端权威响应可让标题、favicon 与各页面立即更新。
    current.value = res.data
    name.value = res.data.name
    apply(res.data)
    clearFileInput()
    message.success('品牌设置已保存')
  }
  catch (error: any) {
    message.error(errorText(error, '保存品牌设置失败'))
  }
  finally {
    saving.value = false
  }
}

async function deleteIcon() {
  deleting.value = true
  try {
    const res = await brandingApi.deleteIcon()
    current.value = res.data
    name.value = res.data.name
    apply(res.data)
    clearFileInput()
    message.success('自定义图标已删除')
  }
  catch (error: any) {
    message.error(errorText(error, '删除自定义图标失败'))
  }
  finally {
    deleting.value = false
  }
}

onMounted(load)
onBeforeUnmount(revokePreview)

defineExpose({
  load,
  save,
  deleteIcon,
  onIconChange,
  clearFileInput,
  name,
  current,
  iconFile,
  previewUrl,
  loading,
  saving,
  deleting,
})
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between gap-3">
        <h3 class="text-lg font-medium">站点品牌</h3>
        <UBadge :color="current.customIcon ? 'success' : 'neutral'" variant="soft">
          {{ current.customIcon ? '自定义图标' : '默认图标' }}
        </UBadge>
      </div>
    </template>

    <div v-if="loading" class="text-sm text-muted">加载中…</div>

    <div v-else class="space-y-5 max-w-3xl">
      <UFormField label="站点名称" name="branding-name" help="将显示在浏览器标题、登录页、侧边栏与监控大屏。">
        <UInput
          v-model="name"
          class="w-full"
          :disabled="saving || deleting"
          maxlength="100"
          placeholder="Site Guard"
        />
      </UFormField>

      <UFormField label="站点图标" name="branding-icon" help="支持 PNG、JPEG，最大 2 MiB。">
        <div class="flex flex-wrap items-center gap-4">
          <div class="flex h-16 w-16 flex-shrink-0 items-center justify-center overflow-hidden rounded-xl bg-success/10 ring-1 ring-success/20">
            <img
              v-if="previewUrl"
              :src="previewUrl"
              alt="待上传图标预览"
              class="h-12 w-12 object-contain"
            >
            <BrandIcon
              v-else
              :src="current.iconUrl"
              :custom="current.customIcon"
              :alt="`${current.name} 图标`"
              class="h-10 w-10 text-success"
            />
          </div>

          <div class="min-w-0 flex-1 space-y-2">
            <input
              ref="fileInput"
              type="file"
              accept="image/png,image/jpeg"
              :disabled="saving || deleting"
              class="block w-full text-sm text-muted file:mr-3 file:rounded-md file:border-0 file:bg-elevated file:px-3 file:py-2 file:text-sm file:text-highlighted"
              data-testid="branding-icon-input"
              @change="onIconChange"
            >
            <p v-if="iconFile" class="truncate text-xs text-muted" :title="iconFile.name">
              待上传：{{ iconFile.name }}
            </p>
          </div>
        </div>
      </UFormField>

      <div class="flex flex-wrap justify-end gap-2">
        <UButton
          v-if="current.customIcon"
          color="error"
          variant="outline"
          :loading="deleting"
          :disabled="saving"
          @click="deleteIcon"
        >
          删除自定义图标
        </UButton>
        <UButton
          color="primary"
          :loading="saving"
          :disabled="deleting"
          @click="save"
        >
          保存品牌设置
        </UButton>
      </div>
    </div>
  </UCard>
</template>
