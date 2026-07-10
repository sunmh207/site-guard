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
})
