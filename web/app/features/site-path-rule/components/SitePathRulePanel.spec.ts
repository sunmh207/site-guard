/// SitePathRulePanel 组件测试。
///
/// 桩掉 @nuxt/ui 组件（UTable / UButton / UInput / UCard 等），通过 shallowMount 验证：
/// - 空规则时显示"暂无规则"提示
/// - 加载到规则后显示规则信息
/// - "添加一行"按钮新增一行
/// - "保存"按钮调 setPathRules API
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { uiStubs } from '~/test/ui-stubs'

/// 替换底层 admin-api-client，使组件调用走我们的桩响应。
/// 这样既不需要在测试里反复 vi.mock `site-path-rule.api` 的相对路径，
/// 也能让 wrapper 触发到的 $fetch / $adminApi 都不再触及真实 Nuxt 上下文。
const $adminApiMock = vi.fn()

vi.mock('~/api/admin-api-client', () => ({
  $adminApi: (...args: unknown[]) => $adminApiMock(...args),
  useAdminApi: () => ({}),
}))

import SitePathRulePanel from './SitePathRulePanel.vue'

const stubs = {
  ...uiStubs,
  // UCard 覆盖：补上 #footer slot，让保存按钮在 wrapper.text() 中可见
  UCard: {
    template:
      '<div>'
      + '<slot name="header" />'
      + '<slot />'
      + '<slot name="footer" />'
      + '</div>',
  },
  UButton: { props: ['label'], template: '<button>{{ label }}<slot /></button>' },
  UFormField: { template: '<div><slot /></div>' },
  /// USelect 桩：渲染原生 <select>，按 items 展开 option，并通过 change 事件
  /// 把新值 emit 出去，使 v-model="row.original.checkType" 真正生效。
  /// items 为 { value, label } 对象数组（与组件实际传入格式一致）。
  USelect: {
    props: ['modelValue', 'items'],
    template:
      '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)">'
      + '<option v-for="it in items" :key="it.value" :value="it.value">{{ it.label }}</option>'
      + '</select>',
  },
  /// UInput 桩加 @input 双向绑定，让 setValue('...') 真正驱动 modelValue 变化。
  UInput: {
    props: ['modelValue', 'type', 'placeholder', 'name', 'modelModifiers'],
    template:
      '<input :value="modelValue" :name="name" :type="type" :placeholder="placeholder"'
      + ' @input="$emit(\'update:modelValue\', $event.target.value)" />'
      + '<span class="uinput-value">{{ modelValue }}</span>',
  },
}

describe('SitePathRulePanel', () => {
  beforeEach(() => {
    $adminApiMock.mockReset()
  })

  it('loads rules on mount and renders rows', async () => {
    $adminApiMock.mockResolvedValueOnce({
      data: [
        { id: 1, siteId: 7, path: '/app_dev.php', expectedHttpStatus: 200,
          lastCheckedAt: 1, lastHttpStatus: 200, lastErrorMessage: null, alertingSince: null },
      ],
    })
    const wrapper = mount(SitePathRulePanel, {
      props: { siteId: 7 },
      global: { stubs },
    })
    await flushPromises()

    expect($adminApiMock).toHaveBeenCalledWith(
      '/site/7/pathRules/get',
      expect.objectContaining({ method: undefined }),
    )
    // UTable 桩渲染每行的每个 accessorKey：'/app_dev.php' 和 200 都应该出现在文本中
    expect(wrapper.text()).toContain('/app_dev.php')
    expect(wrapper.text()).toContain('200')
  })

  it('empty state shows hint when no rules', async () => {
    $adminApiMock.mockResolvedValueOnce({ data: [] })
    const wrapper = mount(SitePathRulePanel, {
      props: { siteId: 7 },
      global: { stubs },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('暂无规则')
  })

  it('addRow pushes a new row with default path', async () => {
    $adminApiMock.mockResolvedValueOnce({ data: [] })
    const wrapper = mount(SitePathRulePanel, {
      props: { siteId: 7 },
      global: { stubs },
    })
    await flushPromises()

    // 找到"添加一行"按钮并点击
    const addBtn = wrapper.findAll('button').find(b => b.text().includes('添加'))
    expect(addBtn).toBeDefined()
    await addBtn!.trigger('click')

    // 新行默认 path = '/'
    expect(wrapper.text()).toContain('/')
  })

  it('save button calls setPathRules with current rows', async () => {
    // 第一次调用：listPathRules → 返回空
    // 第二次调用：setPathRules → 返回成功
    // 第三次调用：save 完成后会再调一次 load → 返回空
    $adminApiMock.mockResolvedValueOnce({ data: [] })
    $adminApiMock.mockResolvedValueOnce({ data: null })
    $adminApiMock.mockResolvedValueOnce({ data: [] })
    const wrapper = mount(SitePathRulePanel, {
      props: { siteId: 7 },
      global: { stubs },
    })
    await flushPromises()

    // 添加一行
    const addBtn = wrapper.findAll('button').find(b => b.text().includes('添加'))
    await addBtn!.trigger('click')

    // 保存
    const saveBtn = wrapper.findAll('button').find(b => b.text().includes('保存'))
    expect(saveBtn).toBeDefined()
    await saveBtn!.trigger('click')
    await flushPromises()

    // 找到 setPathRules 那次调用（path 包含 /set）
    const setCall = $adminApiMock.mock.calls.find(
      call => typeof call[0] === 'string' && (call[0] as string).includes('/set'),
    )
    expect(setCall).toBeDefined()
    expect(setCall![0]).toBe('/site/7/pathRules/set')
    expect(setCall![1]).toMatchObject({ method: 'POST' })
    const body = (setCall![1] as { body: { rules: unknown[] } }).body
    expect(Array.isArray(body.rules)).toBe(true)
    expect(body.rules.length).toBe(1)
  })

  it('renders checkType select and switches input between status code / keyword', async () => {
    $adminApiMock.mockResolvedValueOnce({
      data: [
        { id: 1, siteId: 7, path: '/api/home', expectedHttpStatus: 200,
          checkType: 'KEYWORD', expectedText: 'SiteGuard',
          lastCheckedAt: null, lastHttpStatus: null, lastTextMatched: null,
          lastErrorMessage: null, alertingSince: null },
      ],
    })
    const wrapper = mount(SitePathRulePanel, {
      props: { siteId: 7 },
      global: { stubs },
    })
    await flushPromises()

    // 列表应展示关键字文本
    expect(wrapper.text()).toContain('SiteGuard')
  })

  it('save sends checkType + expectedText for KEYWORD rules', async () => {
    $adminApiMock.mockResolvedValueOnce({ data: [] })
    $adminApiMock.mockResolvedValueOnce({ data: null })
    $adminApiMock.mockResolvedValueOnce({ data: [] })
    const wrapper = mount(SitePathRulePanel, {
      props: { siteId: 7 },
      global: { stubs },
    })
    await flushPromises()

    // 添加一行
    const addBtn = wrapper.findAll('button').find(b => b.text().includes('添加'))
    await addBtn!.trigger('click')

    // 找到 checkType 选择器并切换为 KEYWORD
    const select = wrapper.find('select')
    expect(select.exists()).toBe(true)
    await select.setValue('KEYWORD')
    await select.trigger('change')

    // 找到关键字输入并填入
    const keywordInput = wrapper.find('input[name="expectedText"]')
    expect(keywordInput.exists()).toBe(true)
    await keywordInput.setValue('SiteGuard')

    // 保存
    const saveBtn = wrapper.findAll('button').find(b => b.text().includes('保存'))
    await saveBtn!.trigger('click')
    await flushPromises()

    const setCall = $adminApiMock.mock.calls.find(
      call => typeof call[0] === 'string' && (call[0] as string).includes('/set'),
    )
    expect(setCall).toBeDefined()
    const body = (setCall![1] as { body: { rules: Array<Record<string, unknown>> } }).body
    expect(body.rules[0].checkType).toBe('KEYWORD')
    expect(body.rules[0].expectedText).toBe('SiteGuard')
  })
})
