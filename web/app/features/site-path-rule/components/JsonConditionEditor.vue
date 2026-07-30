<script setup lang="ts">
import type { JsonAssertionConfigDto, JsonConditionDto, JsonConditionOperator } from '../types/site-path-rule.dto'

const model = defineModel<JsonAssertionConfigDto>({ required: true })

const operatorItems: Array<{ value: JsonConditionOperator, label: string }> = [
  { value: 'IS_TRUE', label: '为 true' },
  { value: 'IS_FALSE', label: '为 false' },
  { value: 'NUMBER_EQ', label: '数字等于' },
  { value: 'NUMBER_NE', label: '数字不等于' },
  { value: 'NUMBER_GT', label: '数字大于' },
  { value: 'NUMBER_GTE', label: '数字大于等于' },
  { value: 'NUMBER_LT', label: '数字小于' },
  { value: 'NUMBER_LTE', label: '数字小于等于' },
  { value: 'STRING_EQ', label: '文本等于' },
  { value: 'STRING_NE', label: '文本不等于' },
  { value: 'STRING_CONTAINS', label: '文本包含' },
  { value: 'STRING_NOT_CONTAINS', label: '文本不包含' },
  { value: 'EXISTS', label: '字段存在' },
  { value: 'NOT_EXISTS', label: '字段不存在' },
  { value: 'IS_NULL', label: '值为 null' },
  { value: 'IS_NOT_NULL', label: '值不为 null' },
]

const valueOperators = new Set<JsonConditionOperator>([
  'NUMBER_EQ', 'NUMBER_NE', 'NUMBER_GT', 'NUMBER_GTE', 'NUMBER_LT', 'NUMBER_LTE',
  'STRING_EQ', 'STRING_NE', 'STRING_CONTAINS', 'STRING_NOT_CONTAINS',
])

function addCondition() {
  model.value.conditions.push({ path: '', operator: 'IS_TRUE', expectedValue: null })
}

function removeCondition(index: number) {
  model.value.conditions.splice(index, 1)
}

function onOperatorChanged(condition: JsonConditionDto) {
  if (!valueOperators.has(condition.operator)) condition.expectedValue = null
  else if (condition.expectedValue == null) condition.expectedValue = ''
}

function needsValue(operator: JsonConditionOperator) {
  return valueOperators.has(operator)
}

function isNumberOperator(operator: JsonConditionOperator) {
  return operator.startsWith('NUMBER_')
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center gap-3">
      <span class="text-sm text-(--ui-text-muted)">满足方式</span>
      <USelect
        v-model="model.combinator"
        :items="[{ value: 'ALL', label: '全部满足' }, { value: 'ANY', label: '任一满足' }]"
        class="w-40"
      />
      <UButton icon="i-lucide-plus" label="添加条件" color="neutral" variant="soft" @click="addCondition" />
    </div>

    <div
      v-for="(condition, index) in model.conditions"
      :key="index"
      class="grid grid-cols-[minmax(180px,1fr)_180px_minmax(150px,1fr)_auto] items-center gap-2"
    >
      <UInput v-model="condition.path" placeholder="例如 diskAvailableSpaceRate" />
      <USelect
        v-model="condition.operator"
        :items="operatorItems"
        @update:model-value="onOperatorChanged(condition)"
      />
      <UInput
        v-if="needsValue(condition.operator)"
        v-model="condition.expectedValue"
        :type="isNumberOperator(condition.operator) ? 'number' : 'text'"
        :placeholder="isNumberOperator(condition.operator) ? '例如 10' : '期望文本'"
      />
      <span v-else class="text-sm text-(--ui-text-muted)">无需期望值</span>
      <UButton
        icon="i-lucide-trash-2"
        color="error"
        variant="ghost"
        aria-label="删除条件"
        @click="removeCondition(index)"
      />
    </div>

    <div v-if="model.conditions.length === 0" class="rounded-md border border-dashed p-4 text-sm text-(--ui-text-muted)">
      至少添加一条条件。
    </div>
    <p class="text-xs text-(--ui-text-muted)">
      字段路径示例：checkCrontab、data.value、disks[0].rate。暂不支持数组筛选、通配符、递归或脚本。
    </p>
  </div>
</template>
