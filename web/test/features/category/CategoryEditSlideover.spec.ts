/// 分类新建/重命名抽屉（双模式）组件测试。
///
/// 验证：
///   - 名称为空时显示校验错误
///   - create 模式调用 adminCategoryApi.create({ parentId, name })
///   - update 模式调用 adminCategoryApi.update({ id, name })
///   - 保存成功 emit('ok')
///
/// 实现要点：
///   - useMessage / watch 都是 Nuxt auto-import，必须在测试环境里手动挂到 globalThis
///   - adminCategoryApi 用 vi.mock 整体替换
///   - USlideover / UFormField / UInput / UButton 走 global.stubs 直接渲染为同名标签
///   - handleSave 通过 vm 直接调用，规避 UButton stub 无法触发 click 的问题
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

/// 替换 adminCategoryApi，避免触发对真实 API 客户端的引用
vi.mock('~/features/category/api/category.api', () => ({
  adminCategoryApi: {
    create: vi.fn().mockResolvedValue({ id: 10, name: '浙江' }),
    update: vi.fn().mockResolvedValue({ id: 1, name: '我的' }),
  },
}))

import CategoryEditSlideover from '~/features/category/components/CategoryEditSlideover.vue'
import { adminCategoryApi } from '~/features/category/api/category.api'
import type { CategoryTreeNode } from '~/features/category/types/category.dto'

/// 把 Nuxt UI 组件替成简单 stub：渲染为同名标签，便于 wrapper.find 定位
const stubs = {
  UButton: true,
  UInput: true,
  UFormField: true,
  USlideover: true,
}

describe('CategoryEditSlideover', () => {
  it('shows error when name is empty', async () => {
    const w = mount(CategoryEditSlideover, {
      props: { parent: null, node: null },
      global: { stubs },
    })
    /// 直接调 handleSave：默认 formName 为 ''，应当走空名校验分支
    await (w.vm as any).handleSave()
    /// formError 是 ref，错误文案应当写入；模板里 #body 槽被 USlideover stub 吃掉了，
    /// 这里直接断言 reactive 状态
    expect((w.vm as any).formError).toBe('请输入分类名称')
    /// 校验失败时不应触发 API
    expect(adminCategoryApi.create).not.toHaveBeenCalled()
    expect(adminCategoryApi.update).not.toHaveBeenCalled()
  })

  it('create mode calls adminCategoryApi.create with parentId and name', async () => {
    const parent: CategoryTreeNode = {
      id: 1, parentId: null, name: '默认', systemFlag: true, seq: 0, siteCount: 0, children: [],
    }
    const w = mount(CategoryEditSlideover, {
      props: { parent, node: null },
      global: { stubs },
    })
    /// 绕过 watch(open) 的初始化，直接给 formName 赋值
    ;(w.vm as any).formName = '浙江'
    await (w.vm as any).handleSave()
    expect(adminCategoryApi.create).toHaveBeenCalledWith({ parentId: 1, name: '浙江' })
  })

  it('update mode calls adminCategoryApi.update with id and name', async () => {
    const node: CategoryTreeNode = {
      id: 5, parentId: 1, name: '旧名', systemFlag: false, seq: 0, siteCount: 0, children: [],
    }
    const w = mount(CategoryEditSlideover, {
      props: { parent: null, node },
      global: { stubs },
    })
    ;(w.vm as any).formName = '新名'
    await (w.vm as any).handleSave()
    expect(adminCategoryApi.update).toHaveBeenCalledWith({ id: 5, name: '新名' })
  })

  it('emits ok after successful save', async () => {
    const w = mount(CategoryEditSlideover, {
      props: { parent: null, node: null },
      global: { stubs },
    })
    ;(w.vm as any).formName = '新建'
    await (w.vm as any).handleSave()
    expect(w.emitted('ok')).toBeTruthy()
  })
})
