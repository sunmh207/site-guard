import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { uiStubs } from '~/test/ui-stubs'
import SiteDomainCell from '~/features/site/components/SiteDomainCell.vue'

const ONE_DAY_MS = 1000 * 60 * 60 * 24

function daysFromNow(days: number): number {
  // 加 1 小时缓冲，避免 Date.now() 在组件内被二次读取时因毫秒级抖动
  // 让 Math.floor((ts - now) / ONE_DAY_MS) 少算一天。
  return Date.now() + days * ONE_DAY_MS + 60 * 60 * 1000
}

describe('SiteDomainCell', () => {
  it('renders 未检测 when expiresAt is null', () => {
    const wrapper = mount(SiteDomainCell, {
      props: { expiresAt: null },
      global: { components: uiStubs },
    })
    expect(wrapper.text()).toContain('未检测')
  })

  it('renders 未检测 when expiresAt is undefined', () => {
    const wrapper = mount(SiteDomainCell, {
      global: { components: uiStubs },
    })
    expect(wrapper.text()).toContain('未检测')
  })

  it('renders error with 已过期 label when already expired', () => {
    const wrapper = mount(SiteDomainCell, {
      props: { expiresAt: daysFromNow(-5) },
      global: { components: uiStubs },
    })
    expect(wrapper.text()).toContain('已过期')
    expect(wrapper.text()).toContain('5')
  })

  it('renders error when within 3 days', () => {
    const wrapper = mount(SiteDomainCell, {
      props: { expiresAt: daysFromNow(2) },
      global: { components: uiStubs },
    })
    // 不显示 "已过期"，仅显示天数
    expect(wrapper.text()).not.toContain('已过期')
    expect(wrapper.text()).toContain('2')
  })

  it('renders warning when within 15 days', () => {
    const wrapper = mount(SiteDomainCell, {
      props: { expiresAt: daysFromNow(10) },
      global: { components: uiStubs },
    })
    expect(wrapper.text()).toContain('10')
  })

  it('renders success when more than 15 days away', () => {
    const wrapper = mount(SiteDomainCell, {
      props: { expiresAt: daysFromNow(60) },
      global: { components: uiStubs },
    })
    expect(wrapper.text()).toContain('60')
  })
})