<script setup lang="ts">
/// 站点新建/编辑抽屉。
///
/// 用法：
///   <SiteEditSlideover v-model:open="openCreate" :site="null" @ok="onSaved" />
///   <SiteEditSlideover v-model:open="openEdit" :site="row" @ok="onSaved" />
///
/// props.site 为 null 时为新建模式，否则为编辑模式。
/// props.prefill 在 create 模式下生效：传入时用其预填三个字段；省略时等同于"全新创建"。
/// 表单使用本地校验规则（避免 valibot schema 在抽屉反复初始化时引入时序问题）。
import { adminSiteApi } from '../api/site.api'
import type { SiteDto } from '../types/site.dto'

const props = withDefaults(defineProps<{
  /// 编辑模式时传入待编辑的 SiteDto；创建模式时为 null
  site: SiteDto | null
  /// 创建模式下的可选预填源（复制场景使用）。三个字段都会被写入对应 form ref
  prefill?: { name: string, url: string, categoryId: number } | null
  /// 分类下拉选项（来自父页 useCategoryTree.options）
  categoryOptions?: { value: number, label: string }[]
  /// 默认分类 ID（用于创建模式兜底）
  defaultCategoryId?: number
}>(), {
  /// 复制场景由父页注入；不传 = 旧行为（空表单）
  prefill: null,
  /// Bundle 10 由父页注入；此处给空数组让未传参时不崩
  categoryOptions: () => [],
  /// 创建模式下没有默认分类时回退为 0，后端再兜底为"默认分类"
  defaultCategoryId: 0,
})

const emit = defineEmits<{
  /// 保存成功事件，父页面应当刷新列表
  ok: []
}>()

const message = useMessage()
const open = defineModel<boolean>('open', { default: false })

/// 当前模式：create / update
const mode = computed<'create' | 'update'>(() => (props.site ? 'update' : 'create'))
const loading = ref(false)

const formName = ref('')
const formUrl = ref('')
const formCategoryId = ref<number | null>(null)
/// 连续失败阈值表单值；用字符串以便区分"未填(null)"与"已填数字"
/// 编辑模式下从 props.site.consecutiveFailuresBeforeAlert 读取；创建模式无现有值，留 null
const formConsecutiveFailuresBeforeAlert = ref<number | null>(null)
const formError = ref<string | null>(null)

/// 抽屉打开时根据模式填充表单
watch(open, (v) => {
  if (!v)
    return
  if (mode.value === 'update' && props.site) {
    formName.value = props.site.name
    formUrl.value = props.site.url
    formCategoryId.value = props.site.categoryId
    /// 编辑模式：直接读取实体上的阈值（null/undefined 都视作"使用全局默认"）
    formConsecutiveFailuresBeforeAlert.value = props.site.consecutiveFailuresBeforeAlert ?? null
  }
  else {
    if (props.prefill) {
      /// 复制模式：父页已构造好初始值（name 追加" 复制"），用户可改
      formName.value = props.prefill.name
      formUrl.value = props.prefill.url
      formCategoryId.value = props.prefill.categoryId
    }
    else {
      formName.value = ''
      formUrl.value = ''
      /// 创建模式回退到父页传入的默认分类（典型为 useCategoryTree 的首个分类）
      formCategoryId.value = props.defaultCategoryId
    }
    /// 创建模式：阈值表单回到 null（= 不设置，走全局默认）
    formConsecutiveFailuresBeforeAlert.value = null
  }
  formError.value = null
}, { immediate: true })

/// 本地校验规则，与 schemas/site.schema.ts 严格一致
function validate(): string | null {
  if (!formName.value.trim())
    return '请输入站点名称'
  if (formName.value.length > 128)
    return '站点名称最多 128 个字符'
  if (!formUrl.value.trim())
    return '请输入 URL'
  if (!/^https?:\/\/.+/.test(formUrl.value))
    return 'URL 必须以 http:// 或 https:// 开头'
  if (formUrl.value.length > 512)
    return 'URL 最多 512 个字符'
  /// 同时拒绝 null / undefined / 0（创建模式兜底时 defaultCategoryId 可能为 0）
  if (!formCategoryId.value)
    return '请选择所属分类'
  /// 阈值若填写则必须 ≥ 1（与后端最小值约束保持一致）
  if (formConsecutiveFailuresBeforeAlert.value !== null
    && (!Number.isFinite(formConsecutiveFailuresBeforeAlert.value)
      || formConsecutiveFailuresBeforeAlert.value < 1)) {
    return '连续失败阈值必须 ≥ 1'
  }
  return null
}

async function handleSave() {
  const err = validate()
  if (err) {
    formError.value = err
    return
  }
  formError.value = null
  loading.value = true
  try {
    if (mode.value === 'create') {
      await adminSiteApi.createSite({
        name: formName.value.trim(),
        url: formUrl.value.trim(),
        categoryId: formCategoryId.value!,
        /// null/undefined 都表示"走全局默认"，后端 Service 会视情况回写 null
        consecutiveFailuresBeforeAlert: formConsecutiveFailuresBeforeAlert.value,
      })
      message.success('站点已创建')
    }
    else {
      if (!props.site)
        return
      await adminSiteApi.updateSite({
        id: props.site.id,
        name: formName.value.trim(),
        url: formUrl.value.trim(),
        categoryId: formCategoryId.value!,
        consecutiveFailuresBeforeAlert: formConsecutiveFailuresBeforeAlert.value,
      })
      message.success('站点已更新')
    }
    open.value = false
    emit('ok')
  }
  catch {
    message.error(mode.value === 'create' ? '创建失败' : '更新失败')
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <USlideover
    v-model:open="open"
    :ui="{
      header: 'flex items-center justify-between px-6 py-4 border-b border-default',
      body: 'p-6',
      footer: 'flex items-center justify-end gap-3 px-6 py-4 border-t border-default',
    }"
  >
    <template #header>
      <h2 class="text-base font-medium">
        {{ mode === 'create' ? '新建站点' : '编辑站点' }}
      </h2>
      <UButton color="neutral" variant="ghost" icon="i-lucide-x" size="md" square @click="open = false" />
    </template>

    <template #body>
      <div class="space-y-4">
        <UFormField label="站点名称" name="name" required :ui="{ label: 'text-sm font-normal mb-1' }">
          <UInput
            v-model="formName"
            placeholder="请输入站点名称"
            size="md"
            class="w-full"
            :disabled="loading"
          />
        </UFormField>
        <UFormField label="URL" name="url" required :ui="{ label: 'text-sm font-normal mb-1' }">
          <UInput
            v-model="formUrl"
            placeholder="https://example.com"
            size="md"
            class="w-full"
            :disabled="loading"
          />
        </UFormField>
        <UFormField label="所属分类" name="categoryId" required :ui="{ label: 'text-sm font-normal mb-1' }">
          <USelectMenu
            v-model="formCategoryId"
            :items="categoryOptions"
            value-key="value"
            placeholder="请选择分类"
            :disabled="loading"
          />
        </UFormField>
        <!--
          连续失败阈值（站点级覆盖）：
          - 留空（null）= 走 AlertConfirmSettingCard 配置的全局默认；
          - 填写整数 ≥1 = 该站点的专属阈值。
          这里用字符串中转处理空输入，避免 number 输入框把空字符串转成 0。
        -->
        <UFormField
          label="连续失败阈值"
          name="consecutiveFailuresBeforeAlert"
          hint="留空使用全局默认；最小 1"
          :ui="{ label: 'text-sm font-normal mb-1' }"
        >
          <UInput
            :model-value="formConsecutiveFailuresBeforeAlert ?? ''"
            type="number"
            :min="1"
            size="md"
            class="w-full"
            placeholder="连续 N 次探测失败才触发告警。"
            :disabled="loading"
            @update:model-value="(v) => {
              const raw = (v as string | number)
              formConsecutiveFailuresBeforeAlert =
                raw === '' || raw === null || raw === undefined
                  ? null
                  : Number(raw)
            }"
          />
        </UFormField>
        <!--
          子路由检测已迁移到列表下拉菜单触发的独立 SitePathRuleSlideover，
          不再在本抽屉内嵌；详见 web/app/features/site-path-rule/components/SitePathRuleSlideover.vue
        -->
        <div v-if="formError" class="text-sm text-error">
          {{ formError }}
        </div>
      </div>
    </template>

    <template #footer>
      <UButton label="取消" color="neutral" variant="subtle" :disabled="loading" @click="open = false" />
      <UButton
        :label="mode === 'create' ? '创建' : '保存'"
        color="primary"
        variant="solid"
        :loading="loading"
        @click="handleSave"
      />
    </template>
  </USlideover>
</template>
