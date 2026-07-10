<script setup lang="ts">
/// 删除分类确认模态：要求选 fallbackId（站点迁入目标）。
import { adminCategoryApi } from '../api/category.api'
import type { CategoryOption, CategoryTreeNode } from '../types/category.dto'

const props = defineProps<{
  /// 待删除的分类
  node: CategoryTreeNode | null
  /// 候选迁入分类（来自 useCategoryTree.options）
  options: CategoryOption[]
}>()

const emit = defineEmits<{
  /// 删除成功事件，父页面应当刷新树
  ok: []
}>()

const open = defineModel<boolean>('open', { default: false })
const message = useMessage()
const fallbackId = ref<number | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

/// 排除掉自身
const fallbackOptions = computed(() =>
  props.options.filter(o => o.value !== props.node?.id),
)

watch(open, (v) => {
  if (!v) return
  error.value = null
  fallbackId.value = fallbackOptions.value[0]?.value ?? null
})

async function handleConfirm() {
  if (!props.node || fallbackId.value == null) {
    error.value = '请选择迁入分类'
    return
  }
  loading.value = true
  try {
    await adminCategoryApi.delete({ id: props.node.id, fallbackId: fallbackId.value })
    message.success('分类已删除')
    open.value = false
    emit('ok')
  }
  catch (e: any) {
    error.value = e?.data?.message ?? '删除失败'
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <UModal v-model:open="open" :title="`删除分类：${node?.name ?? ''}`">
    <template #body>
      <div class="space-y-3">
        <p class="text-sm">该分类下如有站点，请选择迁入分类。</p>
        <USelectMenu
          v-model="fallbackId"
          :items="fallbackOptions"
          value-key="value"
          placeholder="选择迁入分类"
        />
        <div v-if="error" class="text-sm text-error">{{ error }}</div>
      </div>
    </template>
    <template #footer>
      <UButton label="取消" color="neutral" variant="subtle" :disabled="loading" @click="open = false" />
      <UButton label="删除" color="error" :loading="loading" @click="handleConfirm" />
    </template>
  </UModal>
</template>
