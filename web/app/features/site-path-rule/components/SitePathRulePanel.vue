<script setup lang="ts">
/// 站点子路由规则面板。
///
/// 两种使用方式：
/// - 默认（bare=false）：以 UCard 形式独立展示，含 header "子路由检测" + "添加一行" 与 footer "保存"
/// - bare=true：不渲染外层 UCard，由父容器（如 SitePathRuleSlideover）提供 header/footer，
///   自身只渲染 "添加一行" / 表格 / 错误 三个区块（保存动作由外层通过 ref.save() 触发，
///   与其他 slideover 的"footer 右下角保存"操作习惯一致）
///
/// 功能：
/// - 加载并展示该站点的全部规则（含探测状态只读字段）
/// - 增删行（修改本地 state）
/// - "保存"动作（通过 expose 的 save() 调用）把全表一次性提交到后端 set 端点
import { onMounted, ref } from 'vue'
import { adminSitePathRuleApi } from '../api/site-path-rule.api'
import type { SitePathRuleDto } from '../types/site-path-rule.dto'
import {UButton} from "#components";

const props = withDefaults(defineProps<{
  siteId: number
  /// true 时不渲染 UCard 外壳，仅渲染表格 + 错误 + 按钮，供 slideover 嵌入
  bare?: boolean
}>(), {
  bare: false,
})

const rules = ref<SitePathRuleDto[]>([])
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref<string | null>(null)

const newRule = (): SitePathRuleDto => ({
  id: null,
  siteId: props.siteId,
  path: '/',
  expectedHttpStatus: 200,
  checkType: 'HTTP_STATUS',
  expectedText: null,
  lastCheckedAt: null,
  lastHttpStatus: null,
  lastTextMatched: null,
  lastErrorMessage: null,
  alertingSince: null,
})

/// 绝对时间格式化（YYYY-MM-DD HH:mm:ss），避免相对时间在冻结页面撒谎。
/// 与项目记忆 feedback_absolute_last_refresh_time 一致。
function formatTimestamp(ms: number | null): string {
  if (ms == null) return '—'
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function load() {
  loading.value = true
  errorMessage.value = null
  try {
    const resp = await adminSitePathRuleApi.listPathRules(props.siteId)
    rules.value = resp.data ?? []
  } catch (e) {
    errorMessage.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

function addRow() {
  rules.value = [...rules.value, newRule()]
}

function removeRow(idx: number) {
  rules.value = rules.value.filter((_, i) => i !== idx)
}

async function save() {
  saving.value = true
  errorMessage.value = null
  try {
    await adminSitePathRuleApi.setPathRules(
      props.siteId,
      rules.value.map(r => ({
        id: r.id,
        siteId: r.siteId,
        path: r.path,
        expectedHttpStatus: r.expectedHttpStatus,
        checkType: r.checkType,
        expectedText: r.expectedText,
        lastCheckedAt: null,
        lastHttpStatus: null,
        lastTextMatched: null,
        lastErrorMessage: null,
        alertingSince: null,
      })),
    )
    await load()
  } catch (e) {
    errorMessage.value = (e as Error).message
  } finally {
    saving.value = false
  }
}

async function deleteRule(id: number) {
  try {
    await adminSitePathRuleApi.deletePathRule(id)
    await load()
  } catch (e) {
    errorMessage.value = (e as Error).message
  }
}

/// 把 save 与 saving 暴露给父容器（如 SitePathRuleSlideover）。
/// bare 模式下父容器需要在 footer 提供"保存"按钮，统一操作习惯。
defineExpose({
  save,
  saving,
})

onMounted(load)
</script>

<script lang="ts">
// 列表列定义（与模板中的 cell slot 一一对应）
// 使用 plain object 数组以避免引入 @tanstack/vue-table 类型依赖
import type { SitePathRuleDto } from '../types/site-path-rule.dto'

const columns: Array<{ accessorKey: keyof SitePathRuleDto | 'actions'; header: string }> = [
  { accessorKey: 'path', header: '路径' },
  { accessorKey: 'checkType', header: '判定类型' },
  { accessorKey: 'expectedText', header: '期望内容' },
  { accessorKey: 'lastTextMatched', header: '上次命中' },
  { accessorKey: 'lastErrorMessage', header: '错误' },
  { accessorKey: 'lastCheckedAt', header: '上次探测时间' },
  { accessorKey: 'alertingSince', header: '当前状态' },
  { accessorKey: 'actions', header: '操作' },
]
</script>

<template>
  <!--
    bare=false：完整 UCard 形态（独立页面场景）
    bare=true：仅渲染表格 + 错误 + 按钮，外层由 slideover 提供 header/footer
  -->
  <UCard v-if="!bare">
    <template #header>
      <div class="flex items-center justify-between">
        <span>子路由检测</span>
        <UButton label="添加子路由" @click="addRow" />
      </div>
    </template>

    <div v-if="loading">加载中...</div>
    <div v-else-if="rules.length === 0">暂无规则，点击"+添加子路由"开始</div>
    <UTable v-else :data="rules" :columns="columns">
      <template #path-cell="{ row }">
        <UInput v-model="row.original.path" />
      </template>
      <template #checkType-cell="{ row }">
        <USelect
          v-model="row.original.checkType"
          :items="[{ value: 'HTTP_STATUS', label: '状态码' }, { value: 'KEYWORD', label: '关键字' }]"
        />
      </template>
      <template #expectedText-cell="{ row }">
        <UInput
          v-if="row.original.checkType === 'KEYWORD'"
          v-model="row.original.expectedText"
          name="expectedText"
          placeholder="响应体包含此文本即正常"
        />
        <UInput
          v-else
          v-model.number="row.original.expectedHttpStatus"
          type="number"
          name="expectedHttpStatus"
        />
      </template>
      <template #lastTextMatched-cell="{ row }">
        <span v-if="row.original.checkType !== 'KEYWORD'">{{ '—' }}</span>
        <UBadge v-else-if="row.original.lastTextMatched === true" color="success" variant="subtle">命中</UBadge>
        <UBadge v-else-if="row.original.lastTextMatched === false" color="error" variant="subtle">未命中</UBadge>
        <span v-else class="text-(--ui-text-muted)">{{ '—' }}</span>
      </template>
      <template #lastErrorMessage-cell="{ row }">
        <span>{{ row.original.lastErrorMessage ?? '—' }}</span>
      </template>
      <template #lastCheckedAt-cell="{ row }">
        <span>{{ formatTimestamp(row.original.lastCheckedAt) }}</span>
      </template>
      <template #alertingSince-cell="{ row }">
        <UBadge v-if="row.original.alertingSince != null" color="error" variant="subtle">
          <UIcon name="i-lucide-alert-circle" class="size-3.5" />
          异常 {{ formatTimestamp(row.original.alertingSince) }}
        </UBadge>
        <span v-else class="text-(--ui-text-muted)">正常</span>
      </template>
      <template #actions-cell="{ row }">
        <UButton
          v-if="row.original.id != null"
          label="删除"
          @click="deleteRule(row.original.id)"
        />
        <UButton v-else label="移除" @click="removeRow(rules.indexOf(row.original))" />
      </template>
    </UTable>

    <div v-if="errorMessage" class="text-error mt-2">{{ errorMessage }}</div>

    <template #footer>
      <div class="flex justify-end">
        <UButton :label="saving ? '保存中...' : '保存'" :disabled="saving" @click="save" />
      </div>
    </template>
  </UCard>

  <div v-else class="space-y-4">
    <div class="flex justify-end">
      <UButton icon="i-lucide-plus" label="添加子路由" @click="addRow" />
    </div>

    <div v-if="loading">加载中...</div>
    <div v-else-if="rules.length === 0">暂无规则，点击"添加子路由"开始</div>
    <UTable v-else :data="rules" :columns="columns">
      <template #path-cell="{ row }">
        <UInput v-model="row.original.path" />
      </template>
      <template #checkType-cell="{ row }">
        <USelect
          v-model="row.original.checkType"
          :items="[{ value: 'HTTP_STATUS', label: '状态码' }, { value: 'KEYWORD', label: '关键字' }]"
        />
      </template>
      <template #expectedText-cell="{ row }">
        <UInput
          v-if="row.original.checkType === 'KEYWORD'"
          v-model="row.original.expectedText"
          name="expectedText"
          placeholder="响应体包含此文本即正常"
        />
        <UInput
          v-else
          v-model.number="row.original.expectedHttpStatus"
          type="number"
          name="expectedHttpStatus"
        />
      </template>
      <template #lastTextMatched-cell="{ row }">
        <span v-if="row.original.checkType !== 'KEYWORD'">{{ '—' }}</span>
        <UBadge v-else-if="row.original.lastTextMatched === true" color="success" variant="subtle">命中</UBadge>
        <UBadge v-else-if="row.original.lastTextMatched === false" color="error" variant="subtle">未命中</UBadge>
        <span v-else class="text-(--ui-text-muted)">{{ '—' }}</span>
      </template>
      <template #lastErrorMessage-cell="{ row }">
        <span>{{ row.original.lastErrorMessage ?? '—' }}</span>
      </template>
      <template #lastCheckedAt-cell="{ row }">
        <span>{{ formatTimestamp(row.original.lastCheckedAt) }}</span>
      </template>
      <template #alertingSince-cell="{ row }">
        <UBadge v-if="row.original.alertingSince != null" color="error" variant="subtle">
          <UIcon name="i-lucide-alert-circle" class="size-3.5" />
          异常 {{ formatTimestamp(row.original.alertingSince) }}
        </UBadge>
        <span v-else class="text-(--ui-text-muted)">正常</span>
      </template>
      <template #actions-cell="{ row }">
        <UButton
          v-if="row.original.id != null"
          label="删除"
          @click="deleteRule(row.original.id)"
        />
        <UButton v-else label="移除" @click="removeRow(rules.indexOf(row.original))" />
      </template>
    </UTable>

    <div v-if="errorMessage" class="text-error">{{ errorMessage }}</div>

    <!--
      bare 模式下不渲染"保存"按钮，由外层 slideover footer 提供（与 SiteEditSlideover 等保持一致）
    -->
  </div>
</template>
