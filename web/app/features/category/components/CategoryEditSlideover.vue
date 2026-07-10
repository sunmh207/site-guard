<script setup lang="ts">
/// 分类新建/重命名抽屉（双模式）。
///
/// props.parent 有值 → create（在其下建子分类）
/// props.node 有值   → update（重命名）
import { adminCategoryApi } from '../api/category.api'
import type { CategoryTreeNode } from '../types/category.dto'

const props = defineProps<{
  /// 父分类：create 模式时新分类挂在它下面，根分类为 null
  parent: CategoryTreeNode | null
  /// 待重命名的分类：update 模式时传入，create 模式时为 null
  node: CategoryTreeNode | null
}>()

const emit = defineEmits<{
  /// 保存成功事件，父页面应当刷新树。
  /// create 模式下携带新建节点（用于自动选中），update 模式下不携带
  ok: [created?: CategoryTreeNode]
}>()

const open = defineModel<boolean>('open', { default: false })
const message = useMessage()
const formName = ref('')
const formError = ref<string | null>(null)
const loading = ref(false)

const isUpdate = computed(() => props.node != null)
const title = computed(() => isUpdate.value ? '重命名分类' : '新建分类')
const parentLabel = computed(() => props.parent ? `所属：${props.parent.name}` : '（根分类）')

watch(open, (v) => {
  if (!v) return
  formName.value = props.node?.name ?? ''
  formError.value = null
}, { immediate: true })

function validate(): string | null {
  const n = formName.value.trim()
  if (!n) return '请输入分类名称'
  if (n.length > 64) return '分类名称最多 64 个字符'
  return null
}

async function handleSave() {
  const err = validate()
  if (err) { formError.value = err; return }
  formError.value = null
  loading.value = true
  try {
    /// create 模式下把新建节点带回给父页面，便于自动选中
    let created: CategoryTreeNode | undefined
    if (isUpdate.value && props.node) {
      await adminCategoryApi.update({ id: props.node.id, name: formName.value.trim() })
      message.success('分类已更新')
    }
    else {
      created = await adminCategoryApi.create({
        parentId: props.parent?.id ?? null,
        name: formName.value.trim(),
      })
      message.success('分类已创建')
    }
    open.value = false
    emit('ok', created)
  }
  catch (e: any) {
    const msg = e?.data?.message ?? e?.message ?? '操作失败'
    formError.value = msg
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <USlideover v-model:open="open">
    <template #header>
      <h2 class="text-base font-medium">{{ title }}</h2>
      <UButton color="neutral" variant="ghost" icon="i-lucide-x" size="md" square @click="open = false" />
    </template>
    <template #body>
      <div class="space-y-4">
        <div v-if="!isUpdate" class="text-sm text-muted">{{ parentLabel }}</div>
        <UFormField label="分类名称" required>
          <UInput v-model="formName" :disabled="loading" maxlength="64" placeholder="例如：浙江" />
        </UFormField>
        <div v-if="formError" class="text-sm text-error">{{ formError }}</div>
      </div>
    </template>
    <template #footer>
      <UButton label="取消" color="neutral" variant="subtle" :disabled="loading" @click="open = false" />
      <UButton :label="isUpdate ? '保存' : '创建'" color="primary" :loading="loading" @click="handleSave" />
    </template>
  </USlideover>
</template>
