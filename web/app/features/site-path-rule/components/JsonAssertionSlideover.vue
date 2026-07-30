<script setup lang="ts">
import { ref, watch } from 'vue'
import { adminSitePathRuleApi } from '../api/site-path-rule.api'
import type { JsonAssertionConfigDto, SitePathRuleDto, SitePathRuleTestResultDto } from '../types/site-path-rule.dto'
import JsonConditionEditor from './JsonConditionEditor.vue'
import JsonConditionTestResult from './JsonConditionTestResult.vue'

const props = defineProps<{
  open: boolean
  siteId: number
  rule: SitePathRuleDto | null
}>()
const emit = defineEmits<{
  'update:open': [value: boolean]
  'apply': [rule: SitePathRuleDto]
}>()

const draft = ref<SitePathRuleDto | null>(null)
const testing = ref(false)
const errorMessage = ref<string | null>(null)
const testResult = ref<SitePathRuleTestResultDto | null>(null)

watch(() => [props.open, props.rule] as const, ([open, rule]) => {
  if (!open || !rule) return
  draft.value = cloneRule(rule)
  draft.value.assertionConfig ??= defaultConfig()
  errorMessage.value = null
  testResult.value = null
}, { immediate: true })

/// UTable 行数据会带 Vue Proxy，不能直接 structuredClone；按 DTO 字段拷贝可稳定生成可编辑草稿。
function cloneRule(rule: SitePathRuleDto): SitePathRuleDto {
  return {
    id: rule.id,
    siteId: rule.siteId,
    path: rule.path,
    expectedHttpStatus: rule.expectedHttpStatus,
    checkType: rule.checkType,
    expectedText: rule.expectedText,
    assertionConfig: rule.assertionConfig
      ? {
          version: rule.assertionConfig.version,
          combinator: rule.assertionConfig.combinator,
          conditions: rule.assertionConfig.conditions.map(condition => ({ ...condition })),
        }
      : null,
    lastCheckedAt: rule.lastCheckedAt,
    lastHttpStatus: rule.lastHttpStatus,
    lastTextMatched: rule.lastTextMatched,
    lastJsonMatched: rule.lastJsonMatched,
    lastJsonDetail: rule.lastJsonDetail,
    lastErrorMessage: rule.lastErrorMessage,
    alertingSince: rule.alertingSince,
  }
}

function defaultConfig(): JsonAssertionConfigDto {
  return {
    version: 1,
    combinator: 'ALL',
    conditions: [{ path: '', operator: 'IS_TRUE', expectedValue: null }],
  }
}

function validate(): string | null {
  if (!draft.value) return '规则不存在'
  if (!draft.value.path.startsWith('/') || draft.value.path.startsWith('//')) return '路径必须以单个 / 开头'
  if (!draft.value.assertionConfig?.conditions.length) return '至少添加一条 JSON 条件'
  if (draft.value.assertionConfig.conditions.some(c => !c.path.trim())) return '字段路径不能为空'
  return null
}

async function test() {
  const error = validate()
  if (error) {
    errorMessage.value = error
    return
  }
  testing.value = true
  errorMessage.value = null
  testResult.value = null
  try {
    const rule = draft.value!
    const response = await adminSitePathRuleApi.testPathRule(props.siteId, {
      path: rule.path,
      expectedHttpStatus: rule.expectedHttpStatus,
      checkType: 'JSON_ASSERT',
      expectedText: null,
      assertionConfig: rule.assertionConfig,
    })
    testResult.value = response.data ?? null
  }
  catch (error) {
    errorMessage.value = (error as Error).message
  }
  finally {
    testing.value = false
  }
}

function apply() {
  const error = validate()
  if (error) {
    errorMessage.value = error
    return
  }
  emit('apply', cloneRule(draft.value!))
  emit('update:open', false)
}
</script>

<template>
  <USlideover
    :open="open"
    :ui="{
      /// JSON 条件编辑器是 4 列布局，默认抽屉宽度会遮住右侧操作列。
      content: 'max-w-4xl',
    }"
    @update:open="emit('update:open', $event)"
  >
    <template #content>
      <div class="flex h-full flex-col">
        <header class="flex items-center justify-between border-b border-(--ui-border) p-4">
          <h2 class="font-semibold">配置 JSON 条件</h2>
          <UButton icon="i-lucide-x" color="neutral" variant="ghost" @click="emit('update:open', false)" />
        </header>
        <div v-if="draft" class="flex-1 space-y-4 overflow-y-auto p-4">
          <div class="rounded-lg border border-(--ui-border) bg-(--ui-bg-muted)/30 p-4">
            <div class="grid gap-4 sm:grid-cols-[minmax(260px,360px)_160px] sm:items-end">
              <UFormField label="子路由路径">
                <UInput v-model="draft.path" class="w-full" />
              </UFormField>
              <UFormField label="期望 HTTP 状态码">
                <UInput v-model.number="draft.expectedHttpStatus" type="number" class="w-full" />
              </UFormField>
            </div>
          </div>
          <JsonConditionEditor v-if="draft.assertionConfig" v-model="draft.assertionConfig" />
          <div class="flex justify-end">
            <UButton label="测试条件" icon="i-lucide-flask-conical" :loading="testing" @click="test" />
          </div>
          <div v-if="errorMessage" class="text-error">{{ errorMessage }}</div>
          <JsonConditionTestResult v-if="testResult" :result="testResult" />
        </div>
        <footer class="flex justify-end gap-2 border-t border-(--ui-border) p-4">
          <UButton label="取消" color="neutral" variant="ghost" @click="emit('update:open', false)" />
          <UButton label="应用" @click="apply" />
        </footer>
      </div>
    </template>
  </USlideover>
</template>
