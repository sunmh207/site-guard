<script setup lang="ts">
import { ref, computed } from 'vue'
import { UPopover } from '#components'
import type { PopoverContentProps } from 'reka-ui'
import type { UIColor, UIButtonVariant } from '~/shared/types/ui'

interface Props {
  /** 标题 */
  title?: string
  /** 描述内容 */
  description?: string
  /** 确认按钮文字 */
  okText?: string
  /** 取消按钮文字 */
  cancelText?: string
  /** 是否显示确认按钮 */
  showOk?: boolean
  /** 是否显示取消按钮 */
  showCancel?: boolean
  /** 主题颜色 */
  color?: UIColor
  /** 确认按钮类型 */
  okColor?: UIColor
  /** 取消按钮类型 */
  cancelColor?: UIColor
  /** 确认按钮样式变体 */
  okVariant?: UIButtonVariant
  /** 取消按钮样式变体 */
  cancelVariant?: UIButtonVariant
  /** 图标 */
  icon?: string
  /** 图标颜色 */
  iconColor?: UIColor
  /** 触发方式 */
  mode?: 'click' | 'hover'
  /** 弹出位置配置 */
  content?: PopoverContentProps
  /** 确认回调函数（支持异步） */
  onOk?: () => void | Promise<void>
  /** 取消回调函数（支持异步） */
  onCancel?: () => void | Promise<void>
}

const props = withDefaults(defineProps<Props>(), {
  okText: '确定',
  cancelText: '取消',
  showOk: true,
  showCancel: true,
  color: 'primary',
  cancelColor: 'neutral',
  okVariant: 'solid',
  cancelVariant: 'outline',
  icon: 'i-lucide-circle-alert',
  mode: 'click',
})

// 计算最终的 okColor，未配置时使用 color
const finalOkColor = computed(() => props.okColor || props.color)

// 计算最终的 iconColor，未配置时使用 color
const finalIconColor = computed(() => props.iconColor || props.color)

const emit = defineEmits<{
  /** 确认事件 */
  ok: []
  /** 取消事件 */
  cancel: []
}>()

// v-model:open - 控制弹出框显示
const open = defineModel<boolean>('open', { default: false })

// 确认按钮 loading 状态
const loading = ref(false)

// 关闭弹出框
const close = () => {
  open.value = false
  loading.value = false
}

// 确认操作
const handleOk = async () => {
  if (!props.onOk) {
    close()
    return
  }

  loading.value = true

  try {
    await props.onOk()
    close()
  } catch (error) {
    loading.value = false
    throw error
  }
}

// 取消操作
const handleCancel = async () => {
  // 优先使用 props.onCancel，如果没有则触发 emit（向后兼容）
  if (props.onCancel) {
    await props.onCancel()
  }
  close()
}
</script>

<template>
  <UPopover v-model:open="open" :mode="mode" :content="content" :arrow="false" :modal="true">
    <slot />
    <!-- 弹出内容 -->
    <template #content>
      <div class="p-4 space-y-3 min-w-3xs max-w-xs">
        <!-- 标题和图标 -->
        <div class="flex items-start gap-3">
          <UIcon v-if="icon" :name="icon" :class="['flex-shrink-0 w-5 h-5 mt-0.5', `text-${finalIconColor}`]" />
          <div class="flex-1 space-y-1">
            <div v-if="title" class="font-medium text-highlighted">
              {{ title }}
            </div>
            <div v-if="description" class="text-sm text-muted">
              {{ description }}
            </div>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div v-if="showOk || showCancel" class="flex items-center justify-end gap-2 pt-1">
          <UButton
            v-if="showCancel"
            :variant="cancelVariant"
            :color="cancelColor"
            size="xs"
            :disabled="loading"
            @click="handleCancel"
          >
            {{ cancelText }}
          </UButton>
          <UButton
            v-if="showOk"
            :variant="okVariant"
            :color="finalOkColor"
            size="xs"
            :loading="loading"
            @click="handleOk"
          >
            {{ okText }}
          </UButton>
        </div>
      </div>
    </template>
  </UPopover>
</template>
