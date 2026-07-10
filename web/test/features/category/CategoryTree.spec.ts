/// 分类树（左栏）组件测试。
///
/// 验证：
///   - 递归渲染根节点 + 嵌套子节点
///   - 点击节点触发 select 事件
///   - 拖放站点到节点触发 drop-sites 事件
import { describe, it, expect } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import CategoryTree from '~/features/category/components/CategoryTree.vue'
import CategoryTreeNode from '~/features/category/components/CategoryTreeNode.vue'
import type { CategoryTreeNode } from '~/features/category/types/category.dto'

/// CategoryTree 内部以驼峰名引用 CategoryTreeNode；happy-dom 不会自动解析同目录组件，
/// 这里显式注册一次，子组件的递归渲染才能跑通
const components = { CategoryTreeNode }

/// 构造一个最小可用分类节点（默认值与 dto 对齐）
function makeNode(id: number, name: string, children: CategoryTreeNode[] = []): CategoryTreeNode {
  return { id, parentId: null, name, systemFlag: false, seq: 0, siteCount: 0, children }
}

describe('CategoryTree', () => {
  /// 一棵三层小树：默认 > 浙江 > 杭州
  const tree: CategoryTreeNode[] = [
    makeNode(1, '默认', [makeNode(2, '浙江', [makeNode(3, '杭州')])]),
  ]

  it('renders root and one level of nested children', async () => {
    const w = mount(CategoryTree, {
      props: { tree, selectedId: null },
      global: { components },
    })
    /// onMounted 异步把根节点加进 expanded；需要等一个 tick 让视图更新
    await nextTick()
    /// 根 + 第一层子节点可见；深层子节点需要用户点击展开箭头才会渲染
    expect(w.text()).toContain('默认')
    expect(w.text()).toContain('浙江')
    expect(w.text()).not.toContain('杭州')
    /// 展开 浙江 之后，第三层 杭州 也应可见
    await w.get('[data-tree-node="2"]').find('button').trigger('click')
    expect(w.text()).toContain('杭州')
  })

  it('emits select on node click', async () => {
    const w = mount(CategoryTree, {
      props: { tree, selectedId: null },
      global: { components },
    })
    await w.get('[data-tree-node="1"]').trigger('click')
    expect(w.emitted('select')?.[0]).toEqual([1])
  })

  it('emits drop-sites with parsed ids on drop', async () => {
    const w = mount(CategoryTree, {
      props: { tree, selectedId: null },
      global: { components },
    })
    /// 构造一个最小的 DataTransfer：只模拟 onDrop 中会用到的 getData / types
    const dt = {
      getData: (type: string) => type === 'text/site-ids' ? JSON.stringify([42, 43]) : '',
      types: ['text/site-ids'],
      setData: () => {},
    } as any
    await w.get('[data-tree-node="1"]').trigger('drop', { dataTransfer: dt })
    expect(w.emitted('drop-sites')?.[0]).toEqual([[42, 43], 1])
  })
})
