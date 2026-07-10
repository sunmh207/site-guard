/// DashboardSkeleton 单测。
///
/// 验证：
/// 1. 默认渲染 5 行表格骨架 + 5 张卡片骨架
/// 2. 自定义 rows：传入数值按预期
/// 3. clamp：rows=1 → 5 行；rows=20 → 10 行
/// 4. 主题 token：根容器不硬写 dark/light（背景走 --ui-bg 等 CSS 变量）
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { uiStubs } from '~/test/ui-stubs'
import DashboardSkeleton from '~/features/site/components/DashboardSkeleton.vue'

describe('DashboardSkeleton', () => {
  it('默认渲染 5 行表格骨架 + 5 张卡片骨架', () => {
    const wrapper = mount(DashboardSkeleton, {
      global: { components: uiStubs },
    })
    // ui-stubs 的 USkeleton 渲染为 <div data-testid="skeleton">
    const skeletons = wrapper.findAll('[data-testid="skeleton"]')
    // 5 张卡片 × 2 段（h-3 + h-8） + 1 张表格卡 × (1 标题 + 5 行 × 4 段) = 10 + 21 = 31
    expect(skeletons.length).toBe(31)
  })

  it('rows=8 → 渲染 8 行表格骨架', () => {
    const wrapper = mount(DashboardSkeleton, {
      props: { rows: 8 },
      global: { components: uiStubs },
    })
    const skeletons = wrapper.findAll('[data-testid="skeleton"]')
    // 10 + (1 + 8 × 4) = 10 + 33 = 43
    expect(skeletons.length).toBe(43)
  })

  it('rows=20 自动 clamp 到 10（上限）', () => {
    const wrapper = mount(DashboardSkeleton, {
      props: { rows: 20 },
      global: { components: uiStubs },
    })
    const skeletons = wrapper.findAll('[data-testid="skeleton"]')
    // 10 + (1 + 10 × 4) = 10 + 41 = 51
    expect(skeletons.length).toBe(51)
  })

  it('rows=1 自动 clamp 到 5（下限）', () => {
    const wrapper = mount(DashboardSkeleton, {
      props: { rows: 1 },
      global: { components: uiStubs },
    })
    const skeletons = wrapper.findAll('[data-testid="skeleton"]')
    // 10 + (1 + 5 × 4) = 10 + 21 = 31（与默认一致）
    expect(skeletons.length).toBe(31)
  })

  it('根容器使用 --ui 主题 token，不硬写 dark/light', () => {
    const wrapper = mount(DashboardSkeleton, {
      global: { components: uiStubs },
    })
    const rootClass = wrapper.classes().join(' ')
    // 应包含 --ui 主题变量绑定；不应硬写 dark: 前缀的 utility
    expect(rootClass).toContain('--ui-bg')
    // 整页所有元素的 class 字符串都不应包含 "dark:" 前缀或 "bg-gray-X00"
    const html = wrapper.html()
    expect(html).not.toMatch(/\bdark:/)
    expect(html).not.toMatch(/bg-gray-\d/)
  })
})
