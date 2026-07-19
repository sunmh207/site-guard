/// SiteEditSlideover 组件测试。
///
/// 验证：
///   - create 模式 + prefill 时三个字段被预填
///   - create 模式 + 不传 prefill 时三个字段为空
///
/// 实现要点（参考 web/test/features/category/CategoryEditSlideover.spec.ts）：
///   - useMessage / watch 是 Nuxt auto-import，需在测试环境手动挂到 globalThis
///   - adminSiteApi 用 vi.mock 整体替换
///   - Nuxt UI 组件走 global.stubs 简化为同名标签
///   - 直接断言 wrapper.vm 内部 reactive 状态（不依赖 stub 渲染的 DOM 内容）
import { watch } from 'vue'

/// 模块顶层：先把 Nuxt auto-import 的全局补上（先于组件 import 跑完）
const noop = () => {}
;(globalThis as any).useMessage = () => ({
  success: noop,
  error: noop,
  info: noop,
  warning: noop,
})
;(globalThis as any).watch = watch

import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'

/// 替换 adminSiteApi，避免触发对真实 API 客户端的引用
vi.mock('~/features/site/api/site.api', () => ({
  adminSiteApi: {
    createSite: vi.fn().mockResolvedValue({ id: 1 }),
    updateSite: vi.fn().mockResolvedValue({ id: 1 }),
  },
}))

import SiteEditSlideover from '~/features/site/components/SiteEditSlideover.vue'

/// Nuxt UI 组件简化为同名标签 stub（不关心渲染内容，关心 props 路径）
const stubs = {
  UButton: true,
  UInput: true,
  UFormField: true,
  USelectMenu: true,
  USlideover: true,
  // Nuxt UI v4 开关组件是 USwitch（不是 v3 的 UToggle）；本抽屉用 UAccordion 折叠证书降级开关 / 运维时段
  USwitch: true,
  UAccordion: true,
  // 运维时段适用日 7 个复选框
  UCheckbox: true,
}

describe('SiteEditSlideover - prefill', () => {
  it('create 模式 + prefill 时三个字段被预填', () => {
    const w = mount(SiteEditSlideover, {
      props: {
        open: true,
        site: null,
        prefill: { name: '官网 复制', url: 'https://example.com', categoryId: 2 },
      },
      global: { stubs },
    })
    expect((w.vm as any).formName).toBe('官网 复制')
    expect((w.vm as any).formUrl).toBe('https://example.com')
    expect((w.vm as any).formCategoryId).toBe(2)
  })

  it('create 模式 + 不传 prefill 时字段为空，categoryId 回退到 defaultCategoryId', () => {
    const w = mount(SiteEditSlideover, {
      props: { open: true, site: null, defaultCategoryId: 7 },
      global: { stubs },
    })
    expect((w.vm as any).formName).toBe('')
    expect((w.vm as any).formUrl).toBe('')
    expect((w.vm as any).formCategoryId).toBe(7)
  })

  it('create 模式默认运维时段禁用', () => {
    const w = mount(SiteEditSlideover, {
      props: { open: true, site: null },
      global: { stubs },
    })
    expect((w.vm as any).formMaintenanceEnabled).toBe(false)
  })

  it('update 模式 + 站点已有 maintenance 时回显控件', () => {
    const w = mount(SiteEditSlideover, {
      props: {
        open: true,
        site: {
          id: 1,
          name: '官网',
          url: 'https://example.com',
          categoryId: 1,
          maintenance: '{"start":"22:00","end":"08:00","days":["MON","TUE"]}',
        },
      },
      global: { stubs },
    })
    expect((w.vm as any).formMaintenanceEnabled).toBe(true)
    expect((w.vm as any).formMaintenanceStart).toBe('22:00')
    expect((w.vm as any).formMaintenanceEnd).toBe('08:00')
    expect((w.vm as any).formMaintenanceDays).toEqual(['MON', 'TUE'])
  })

  it('toggleWeekDay:勾选添加 / 取消移除', () => {
    const w = mount(SiteEditSlideover, {
      props: { open: true, site: null },
      global: { stubs },
    })
    const vm = w.vm as any
    // 初始空(全周)
    expect(vm.formMaintenanceDays).toEqual([])
    // 勾选 MON
    vm.toggleWeekDay('MON', true)
    expect(vm.formMaintenanceDays).toEqual(['MON'])
    // 勾选 FRI
    vm.toggleWeekDay('FRI', true)
    expect(vm.formMaintenanceDays).toEqual(['MON', 'FRI'])
    // 取消 MON
    vm.toggleWeekDay('MON', false)
    expect(vm.formMaintenanceDays).toEqual(['FRI'])
    // 重复勾选幂等
    vm.toggleWeekDay('FRI', true)
    expect(vm.formMaintenanceDays).toEqual(['FRI'])
  })
})
