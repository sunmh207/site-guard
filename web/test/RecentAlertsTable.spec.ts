import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { uiStubs } from '~/test/ui-stubs'
import RecentAlertsTable from '~/features/site/components/RecentAlertsTable.vue'
import type { RecentAlert } from '~/features/site/types/site-stats.dto'

const baseAlert: Omit<RecentAlert, 'kind'> = {
  siteId: 1,
  siteName: '官网',
  siteUrl: 'https://example.com',
  status: 'ABNORMAL',
  detectedAt: Date.now(),
  message: 'something',
}

describe('RecentAlertsTable', () => {
  it('renders empty state when alerts is empty', () => {
    const wrapper = mount(RecentAlertsTable, {
      props: { alerts: [] },
      global: { components: uiStubs },
    })
    expect(wrapper.text()).toContain('暂无异常')
  })

  it('renders AVAILABILITY kind with activity label', () => {
    const wrapper = mount(RecentAlertsTable, {
      props: {
        alerts: [
          { ...baseAlert, kind: 'AVAILABILITY', message: 'HTTP 500: 连接被拒绝' },
        ],
      },
      global: { components: uiStubs },
    })
    expect(wrapper.text()).toContain('可用性')
    expect(wrapper.text()).toContain('HTTP 500: 连接被拒绝')
  })

  it('renders CERT_EXPIRY kind with shield-alert label', () => {
    const wrapper = mount(RecentAlertsTable, {
      props: {
        alerts: [
          { ...baseAlert, kind: 'CERT_EXPIRY', message: '证书已过期 3 天' },
        ],
      },
      global: { components: uiStubs },
    })
    expect(wrapper.text()).toContain('证书')
    expect(wrapper.text()).toContain('证书已过期 3 天')
  })

  it('renders DOMAIN_EXPIRING kind with calendar-x label', () => {
    const wrapper = mount(RecentAlertsTable, {
      props: {
        alerts: [
          { ...baseAlert, kind: 'DOMAIN_EXPIRING', message: '域名将于 5 天后过期' },
        ],
      },
      global: { components: uiStubs },
    })
    expect(wrapper.text()).toContain('域名')
    expect(wrapper.text()).toContain('域名将于 5 天后过期')
  })

  it('renders ABNORMAL status text is not displayed (status column removed)', () => {
    const wrapper = mount(RecentAlertsTable, {
      props: {
        alerts: [
          { ...baseAlert, kind: 'AVAILABILITY', status: 'ABNORMAL', message: 'down' },
        ],
      },
      global: { components: uiStubs },
    })
    // 状态列已移除：聚合源只输出 ABNORMAL，展示恒为同色，列冗余
    expect(wrapper.text()).not.toContain('ABNORMAL')
    expect(wrapper.text()).not.toContain('NORMAL')
  })

  it('renders siteUrl inline next to siteName as a clickable link', () => {
    const wrapper = mount(RecentAlertsTable, {
      props: {
        alerts: [
          { ...baseAlert, siteName: '官网', siteUrl: 'https://example.com/login', kind: 'AVAILABILITY' },
        ],
      },
      global: { components: uiStubs },
    })
    expect(wrapper.text()).toContain('官网')
    expect(wrapper.text()).toContain('https://example.com/login')
    // 链接指向 siteUrl，并配置为新页面打开
    const link = wrapper.find('a[href="https://example.com/login"]')
    expect(link.exists()).toBe(true)
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')).toBe('noopener noreferrer')
  })

  it('renders multiple alerts of mixed kinds', () => {
    const alerts: RecentAlert[] = [
      { ...baseAlert, siteId: 1, kind: 'AVAILABILITY',    status: 'ABNORMAL', message: 'm1' },
      { ...baseAlert, siteId: 2, kind: 'CERT_EXPIRY',     status: 'ABNORMAL', message: 'm2' },
      { ...baseAlert, siteId: 3, kind: 'DOMAIN_EXPIRING', status: 'NORMAL',   message: 'm3' },
    ]
    const wrapper = mount(RecentAlertsTable, {
      props: { alerts },
      global: { components: uiStubs },
    })
    expect(wrapper.text()).toContain('m1')
    expect(wrapper.text()).toContain('m2')
    expect(wrapper.text()).toContain('m3')
    expect(wrapper.text()).toContain('可用性')
    expect(wrapper.text()).toContain('证书')
    expect(wrapper.text()).toContain('域名')
  })
})