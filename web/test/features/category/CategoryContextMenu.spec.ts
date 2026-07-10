/// 分类右键菜单组件冒烟测试。
///
/// 组件本身基于 UContextMenu（reka-ui）实现，完整交互测试成本过高。
/// 这里只验证：
///   - node 有值时能正常 mount
///   - node 为 null 时也能正常 mount（items 为空数组，不会渲染菜单项）
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CategoryContextMenu from '~/features/category/components/CategoryContextMenu.vue'
import type { CategoryTreeNode } from '~/features/category/types/category.dto'

const node: CategoryTreeNode = {
  id: 1,
  parentId: null,
  name: '默认',
  systemFlag: true,
  seq: 0,
  siteCount: 0,
  children: [],
}

const allNodes: CategoryTreeNode[] = [node]

describe('CategoryContextMenu', () => {
  it('mounts without error when node is provided', () => {
    const w = mount(CategoryContextMenu, {
      props: { node, allNodes: allNodes },
      slots: { default: '<button>trigger</button>' },
      global: {
        /// UContextMenu 是 Nuxt UI 提供的复合组件，reka-ui 依赖太重，
        /// 测试环境里用简单模板桩替代即可
        stubs: {
          UContextMenu: { template: '<div><slot /></div>' },
        },
      },
    })
    expect(w.exists()).toBe(true)
    /// 触发槽位应被渲染
    expect(w.text()).toContain('trigger')
  })

  it('does not crash when node is null', () => {
    const w = mount(CategoryContextMenu, {
      props: { node: null, allNodes: allNodes },
      slots: { default: '<button>trigger</button>' },
      global: {
        stubs: {
          UContextMenu: { template: '<div><slot /></div>' },
        },
      },
    })
    expect(w.exists()).toBe(true)
  })
})
