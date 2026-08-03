/// 分类树（左栏）组件测试。
///
/// 验证：
///   - 递归渲染根节点 + 嵌套子节点
///   - 点击节点触发 select 事件
///   - 拖放站点到节点触发 drop-sites 事件
///   - 手柄 ⋮⋮ 始终可见（不在 sortMode 控制下；CSS 通过 group-hover 显隐，本测试不验证 CSS）
///   - 同父级 drop 触发 category-reorder（before/after 按 clientY 判定）
///   - 跨父级 drop 不触发 category-reorder
///   - drop 到自身 → 不触发 category-reorder
import { describe, it, expect } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import CategoryTree from '~/features/category/components/CategoryTree.vue'
import CategoryTreeNode from '~/features/category/components/CategoryTreeNode.vue'
import type { CategoryTreeNode } from '~/features/category/types/category.dto'

/// CategoryTree 内部以驼峰名引用 CategoryTreeNode；happy-dom 不会自动解析同目录组件。
const components = { CategoryTreeNode }

function makeNode(id: number, name: string, children: CategoryTreeNode[] = [], parentId: number | null = null): CategoryTreeNode {
  return { id, parentId, name, systemFlag: false, seq: 0, siteCount: 0, children }
}

describe('CategoryTree', () => {
  const tree: CategoryTreeNode[] = [
    makeNode(1, '默认', [makeNode(2, '浙江', [makeNode(3, '杭州')])]),
  ]

  it('renders root and one level of nested children', async () => {
    const w = mount(CategoryTree, {
      props: { tree, selectedId: null },
      global: { components },
    })
    await nextTick()
    expect(w.text()).toContain('默认')
    expect(w.text()).toContain('浙江')
    expect(w.text()).not.toContain('杭州')
    await w.get('[data-tree-node="2"]').find('button').trigger('click')
    expect(w.text()).toContain('杭州')
  })

  it('emits select on node click', async () => {
    const w = mount(CategoryTree, {
      props: { tree, selectedId: null },
      global: { components },
    })
    await nextTick()
    await w.get('[data-tree-node="1"]').trigger('click')
    expect(w.emitted('select')?.[0]).toEqual([1])
  })

  it('emits context-menu on right click', async () => {
    const w = mount(CategoryTree, {
      props: { tree, selectedId: null },
      global: { components },
    })
    await nextTick()
    await w.get('[data-tree-node="1"]').trigger('contextmenu')
    expect(w.emitted('context-menu')?.[0]).toEqual([tree[0], expect.anything()])
  })

  it('emits drop-sites with parsed ids on drop', async () => {
    const w = mount(CategoryTree, {
      props: { tree, selectedId: null },
      global: { components },
    })
    await nextTick()
    const dt = {
      getData: (type: string) => type === 'text/site-ids' ? JSON.stringify([42, 43]) : '',
      types: ['text/site-ids'],
      setData: () => {},
    } as any
    await w.get('[data-tree-node="1"]').trigger('drop', { dataTransfer: dt })
    expect(w.emitted('drop-sites')?.[0]).toEqual([[42, 43], 1])
  })

  it('始终渲染手柄 ⋮⋮（不再受 sortMode 控制）', async () => {
    const w = mount(CategoryTree, {
      props: { tree, selectedId: null },
      global: { components },
    })
    await nextTick()
    expect(w.text()).toContain('⋮⋮')
  })

  it('同父级 drop 触发 category-reorder（before=true 当 clientY < 中线）', async () => {
    const flat: CategoryTreeNode[] = [
      makeNode(1, 'A', [], null),
      makeNode(2, 'B', [], null),
      makeNode(3, 'C', [], null),
    ]
    const w = mount(CategoryTree, {
      props: { tree: flat, selectedId: null },
      global: { components },
    })
    await nextTick()
    const target = w.get('[data-tree-node="2"]').element as HTMLElement
    target.getBoundingClientRect = () => ({
      top: 80, bottom: 120, left: 0, right: 100, width: 100, height: 40, x: 0, y: 80, toJSON: () => {},
    } as any)
    const dt = {
      getData: (type: string) => {
        if (type === 'text/category-id') return '1'
        if (type === 'text/category-parent') return 'root'
        return ''
      },
      types: ['text/category-id', 'text/category-parent'],
      setData: () => {},
    } as any
    await w.get('[data-tree-node="2"]').trigger('drop', { dataTransfer: dt, clientY: 85 })
    expect(w.emitted('category-reorder')?.[0]).toEqual([1, 2, true])
  })

  it('同父级 drop 触发 category-reorder（before=false 当 clientY >= 中线）', async () => {
    const flat: CategoryTreeNode[] = [
      makeNode(1, 'A', [], null),
      makeNode(2, 'B', [], null),
    ]
    const w = mount(CategoryTree, {
      props: { tree: flat, selectedId: null },
      global: { components },
    })
    await nextTick()
    const target = w.get('[data-tree-node="2"]').element as HTMLElement
    target.getBoundingClientRect = () => ({
      top: 80, bottom: 120, left: 0, right: 100, width: 100, height: 40, x: 0, y: 80, toJSON: () => {},
    } as any)
    const dt = {
      getData: (type: string) => {
        if (type === 'text/category-id') return '1'
        if (type === 'text/category-parent') return 'root'
        return ''
      },
      types: ['text/category-id', 'text/category-parent'],
      setData: () => {},
    } as any
    await w.get('[data-tree-node="2"]').trigger('drop', { dataTransfer: dt, clientY: 115 })
    expect(w.emitted('category-reorder')?.[0]).toEqual([1, 2, false])
  })

  it('跨父级 drop 不触发 category-reorder', async () => {
    const mixed: CategoryTreeNode[] = [
      makeNode(1, 'RootA', [makeNode(2, '子A', [], 1)]),
      makeNode(3, 'RootB', [], null),
    ]
    const w = mount(CategoryTree, {
      props: { tree: mixed, selectedId: null },
      global: { components },
    })
    await nextTick()
    const dt = {
      getData: (type: string) => {
        if (type === 'text/category-id') return '2'
        if (type === 'text/category-parent') return '1'
        return ''
      },
      types: ['text/category-id', 'text/category-parent'],
      setData: () => {},
    } as any
    await w.get('[data-tree-node="3"]').trigger('drop', { dataTransfer: dt, clientY: 50 })
    expect(w.emitted('category-reorder')).toBeFalsy()
  })

  it('drop 到自身 → 不触发 category-reorder', async () => {
    const flat: CategoryTreeNode[] = [makeNode(1, 'A', [], null)]
    const w = mount(CategoryTree, {
      props: { tree: flat, selectedId: null },
      global: { components },
    })
    await nextTick()
    const dt = {
      getData: (type: string) => {
        if (type === 'text/category-id') return '1'
        if (type === 'text/category-parent') return 'root'
        return ''
      },
      types: ['text/category-id', 'text/category-parent'],
      setData: () => {},
    } as any
    await w.get('[data-tree-node="1"]').trigger('drop', { dataTransfer: dt, clientY: 50 })
    expect(w.emitted('category-reorder')).toBeFalsy()
  })
})
