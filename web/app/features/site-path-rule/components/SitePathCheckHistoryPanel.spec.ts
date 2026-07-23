/// SitePathCheckHistoryPanel 组件测试。
///
/// 桩掉 @nuxt/ui 组件（UTable / UBadge / UButton / UTooltip），通过 shallowMount 验证：
/// - 空历史时显示"暂无探测历史"提示
/// - 加载到历史后显示记录信息（时间、状态、HTTP 状态码、关键字命中）
/// - 刷新按钮调用 refresh()
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { uiStubs } from '~/test/ui-stubs'

/// 替换底层 admin-api-client，使组件调用走我们的桩响应。
const $adminApiMock = vi.fn()

vi.mock('~/api/admin-api-client', () => ({
  $adminApi: (...args: unknown[]) => $adminApiMock(...args),
  useAdminApi: () => ({}),
}))

import SitePathCheckHistoryPanel from './SitePathCheckHistoryPanel.vue'

const stubs = {
  ...uiStubs,
  UButton: { props: ['label', 'icon'], template: '<button>{{ label }}<slot /></button>' },
}

describe('SitePathCheckHistoryPanel', () => {
  beforeEach(() => {
    $adminApiMock.mockReset()
  })

  it('loads history on mount and renders rows', async () => {
    $adminApiMock.mockResolvedValueOnce([
      {
        id: 1,
        siteId: 7,
        ruleId: 42,
        path: '/api/orders',
        checkedAt: 1_700_000_000_000,
        status: 'UP',
        httpStatus: 200,
        textMatched: null,
        errorMessage: null,
      },
      {
        id: 2,
        siteId: 7,
        ruleId: 42,
        path: '/api/orders',
        checkedAt: 1_699_999_000_000,
        status: 'ERROR',
        httpStatus: null,
        textMatched: null,
        errorMessage: 'timeout after 5s',
      },
    ])
    const wrapper = mount(SitePathCheckHistoryPanel, {
      props: { ruleId: 42 },
      global: { stubs },
    })
    await flushPromises()

    expect($adminApiMock).toHaveBeenCalledWith(
      '/site/pathRule/history/get',
      expect.objectContaining({ query: { ruleId: 42, limit: 30 } }),
    )
    // 表格应展示：状态标签、HTTP 状态码、错误信息
    expect(wrapper.text()).toContain('完成')
    expect(wrapper.text()).toContain('200')
    expect(wrapper.text()).toContain('timeout after 5s')
  })

  it('empty state shows hint when no history', async () => {
    $adminApiMock.mockResolvedValueOnce([])
    const wrapper = mount(SitePathCheckHistoryPanel, {
      props: { ruleId: 42 },
      global: { stubs },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('暂无探测历史')
  })

  it('renders textMatched badges for keyword mode', async () => {
    $adminApiMock.mockResolvedValueOnce([
      {
        id: 1,
        siteId: 7,
        ruleId: 42,
        path: '/welcome',
        checkedAt: 1_700_000_000_000,
        status: 'UP',
        httpStatus: 200,
        textMatched: true,
        errorMessage: null,
      },
      {
        id: 2,
        siteId: 7,
        ruleId: 42,
        path: '/welcome',
        checkedAt: 1_699_999_000_000,
        status: 'UP',
        httpStatus: 200,
        textMatched: false,
        errorMessage: null,
      },
    ])
    const wrapper = mount(SitePathCheckHistoryPanel, {
      props: { ruleId: 42 },
      global: { stubs },
    })
    await flushPromises()

    // 关键字命中：命中 + 未命中 两个 badge 都应出现
    expect(wrapper.text()).toContain('命中')
    expect(wrapper.text()).toContain('未命中')
  })

  it('exposes refresh that re-fetches data', async () => {
    $adminApiMock.mockResolvedValueOnce([])
    const wrapper = mount(SitePathCheckHistoryPanel, {
      props: { ruleId: 42 },
      global: { stubs },
    })
    await flushPromises()

    // 预备第二次响应
    $adminApiMock.mockResolvedValueOnce([
      {
        id: 3,
        siteId: 7,
        ruleId: 42,
        path: '/api',
        checkedAt: 1_700_000_000_000,
        status: 'UP',
        httpStatus: 200,
        textMatched: null,
        errorMessage: null,
      },
    ])

    // 调用暴露的 refresh()
    const panel = wrapper.vm as unknown as { refresh: () => Promise<void> }
    await panel.refresh()
    await flushPromises()

    // 第二次调用已发出
    expect($adminApiMock).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('完成')
  })
})
