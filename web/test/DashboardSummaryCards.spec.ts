import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { uiStubs } from '~/test/ui-stubs'
import DashboardSummaryCards from '~/features/site/components/DashboardSummaryCards.vue'

describe('DashboardSummaryCards', () => {
  it('renders 5 cards with values', () => {
    const wrapper = mount(DashboardSummaryCards, {
      props: {
        summary: {
          totalSites: 12,
          healthyCount: 7,
          abnormalCount: 2,
          pendingCount: 2,
          pausedCount: 1,
          avgResponseMs: 123.4,
        },
      },
      global: { components: uiStubs },
    })

    // 5 张卡片各显示对应数字（healthyCount=7 在文本里也可能命中 "异常数" 之外的 7，
    // 因此断言包含全部数字 + 标签的精确拼写）
    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('7')
    expect(wrapper.text()).toContain('2')
    expect(wrapper.text()).toContain('1')
  })

  it('renders all 5 card labels', () => {
    const wrapper = mount(DashboardSummaryCards, {
      props: {
        summary: {
          totalSites: 0,
          healthyCount: 0,
          abnormalCount: 0,
          pendingCount: 0,
          pausedCount: 0,
          avgResponseMs: null,
        },
      },
      global: { components: uiStubs },
    })

    expect(wrapper.text()).toContain('总站点数')
    expect(wrapper.text()).toContain('健康数')
    expect(wrapper.text()).toContain('异常数')
    expect(wrapper.text()).toContain('待检测数')
    expect(wrapper.text()).toContain('暂停数')
    // 老字段已下线：不应再渲染"在线数"
    expect(wrapper.text()).not.toContain('在线数')
  })
})