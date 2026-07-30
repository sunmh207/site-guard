import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { uiStubs } from '~/test/ui-stubs'
import BrandIcon from './BrandIcon.vue'

describe('BrandIcon', () => {
  it('默认配置渲染雷达图标', () => {
    const wrapper = mount(BrandIcon, {
      props: { src: '/favicon.ico', custom: false },
      global: { components: uiStubs },
    })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('i-lucide-radar')
  })

  it('自定义图片失败后回退雷达，新 URL 到达时重新尝试', async () => {
    const wrapper = mount(BrandIcon, {
      props: { src: '/broken.png', custom: true, alt: '品牌图标' },
      global: { components: uiStubs },
    })

    expect(wrapper.find('img').attributes('src')).toBe('/broken.png')
    await wrapper.find('img').trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('i-lucide-radar')

    await wrapper.setProps({ src: '/new.png' })
    expect(wrapper.find('img').attributes('src')).toBe('/new.png')
  })
})
