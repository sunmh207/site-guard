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
  // UInput stub 同时把 modelValue 渲染成可见文本，便于 wrapper.text() 断言。
  // 声明 type / modelModifiers 等常见 props 以避免 Vue "Extraneous non-props" 警告
  UInput: {
    props: ['modelValue', 'type', 'modelModifiers'],
    template:
      '<input :value="modelValue" />'
      + '<span class="uinput-value">{{ modelValue }}</span>',
  },
  UFormField: { template: '<div><slot /></div>' },
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
})
