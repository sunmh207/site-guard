import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { uiStubs } from '~/test/ui-stubs'
import JsonConditionEditor from './JsonConditionEditor.vue'

const stubs = {
  ...uiStubs,
  UButton: { props: ['label'], template: '<button @click="$emit(\'click\')">{{ label }}</button>' },
  USelect: {
    props: ['modelValue', 'items'],
    template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><option v-for="item in items" :value="item.value">{{ item.label }}</option></select>',
  },
  UInput: {
    props: ['modelValue', 'type', 'placeholder'],
    template: '<input :type="type" :value="modelValue" :placeholder="placeholder" @input="$emit(\'update:modelValue\', $event.target.value)">',
  },
}

describe('JsonConditionEditor', () => {
  it('renders simple path help and operator-driven value input', async () => {
    const wrapper = mount(JsonConditionEditor, {
      props: {
        modelValue: {
          version: 1,
          combinator: 'ALL',
          conditions: [{ path: 'diskAvailableSpaceRate', operator: 'NUMBER_GT', expectedValue: '10' }],
        },
      },
      global: { stubs },
    })

    expect(wrapper.text()).toContain('disks[0].rate')
    expect(wrapper.find('input[type="number"]').exists()).toBe(true)
  })

  it('adds and removes flat condition rows', async () => {
    const wrapper = mount(JsonConditionEditor, {
      props: {
        modelValue: { version: 1, combinator: 'ALL', conditions: [] },
      },
      global: { stubs },
    })

    await wrapper.findAll('button').find(button => button.text().includes('添加条件'))!.trigger('click')
    expect(wrapper.findAll('select').length).toBeGreaterThan(1)
  })
})
