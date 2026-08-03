/// useCategoryTree 单测（reorderWithinParent 部分）。
///
/// 验证：
///   - 调 reorderWithinParent(null, ids) → 根级按 ids 重排
///   - 调 reorderWithinParent(parentId, ids) → 子级按 ids 重排
///   - 乐观更新：调 API 前 tree 已重排
///   - API 失败回滚：tree 回到原顺序，并调 refresh
///   - items seq 步长 100（100, 200, 300...）
///   - 跨父级（parentId 在树中找不到）→ no-op，不调 API
///
/// 测试约定（与同目录其他 spec 一致）：
///   - useMessage / useToast 全局 stub
///   - adminCategoryApi 整模块 mock
///   - 通过 ref() 直接构造 tree 状态后调用方法
import { watch } from 'vue'

const noop = () => {}
;(globalThis as any).useMessage = () => ({
  success: noop,
  error: noop,
  info: noop,
  warning: noop,
  open: noop,
  update: noop,
  remove: noop,
  clear: noop,
})
;(globalThis as any).watch = watch
;(globalThis as any).useToast = () => ({
  add: () => 'toast-1',
  update: noop,
  remove: noop,
  clear: noop,
})

import { describe, it, expect, vi } from 'vitest'

const reorderMock = vi.fn()
const treeMock = vi.fn()

vi.mock('~/features/category/api/category.api', () => ({
  adminCategoryApi: {
    tree: (...args: unknown[]) => treeMock(...args),
    reorder: (...args: unknown[]) => reorderMock(...args),
  },
}))

import { useCategoryTree } from '~/features/category/composables/useCategoryTree'
import type { CategoryTreeNode } from '~/features/category/types/category.dto'

function makeNode(id: number, parentId: number | null, name: string, children: CategoryTreeNode[] = []): CategoryTreeNode {
  return { id, parentId, name, systemFlag: false, seq: 0, siteCount: 0, children }
}

function rootIds(tree: CategoryTreeNode[]): number[] {
  return tree.map(n => n.id)
}

function childIds(tree: CategoryTreeNode[], parentId: number): number[] | null {
  for (const n of tree) {
    if (n.id === parentId) return n.children.map(c => c.id)
    const deeper = childIds(n.children, parentId)
    if (deeper) return deeper
  }
  return null
}

describe('useCategoryTree.reorderWithinParent', () => {
  it('根级调序：按 orderedIds 重排根 children', async () => {
    reorderMock.mockReset()
    reorderMock.mockResolvedValue({ code: 'Ok', data: null, message: null })

    const { tree, reorderWithinParent } = useCategoryTree()
    tree.value = [
      makeNode(1, null, 'A'),
      makeNode(2, null, 'B'),
      makeNode(3, null, 'C'),
    ]
    await reorderWithinParent(null, [3, 1, 2])
    expect(rootIds(tree.value)).toEqual([3, 1, 2])
  })

  it('子级调序：按 orderedIds 重排父节点的 children', async () => {
    reorderMock.mockReset()
    reorderMock.mockResolvedValue({ code: 'Ok', data: null, message: null })

    const { tree, reorderWithinParent } = useCategoryTree()
    tree.value = [
      makeNode(1, null, 'Root', [
        makeNode(2, 1, 'A'),
        makeNode(3, 1, 'B'),
        makeNode(4, 1, 'C'),
      ]),
    ]
    await reorderWithinParent(1, [4, 2, 3])
    expect(childIds(tree.value, 1)).toEqual([4, 2, 3])
  })

  it('items seq 步长 100（按 orderedIds 索引 +1 再乘 100）', async () => {
    reorderMock.mockReset()
    reorderMock.mockResolvedValue({ code: 'Ok', data: null, message: null })

    const { tree, reorderWithinParent } = useCategoryTree()
    tree.value = [
      makeNode(1, null, 'A'),
      makeNode(2, null, 'B'),
      makeNode(3, null, 'C'),
    ]
    await reorderWithinParent(null, [3, 1, 2])
    expect(reorderMock).toHaveBeenCalledWith([
      { id: 3, seq: 100 },
      { id: 1, seq: 200 },
      { id: 2, seq: 300 },
    ])
  })

  it('API 失败回滚：tree 回到原顺序 + 调 refresh', async () => {
    reorderMock.mockReset()
    treeMock.mockReset()
    reorderMock.mockRejectedValueOnce(new Error('network'))
    treeMock.mockResolvedValueOnce([
      makeNode(1, null, 'A'),
      makeNode(2, null, 'B'),
      makeNode(3, null, 'C'),
    ])

    const { tree, reorderWithinParent, refresh } = useCategoryTree()
    tree.value = [
      makeNode(1, null, 'A'),
      makeNode(2, null, 'B'),
      makeNode(3, null, 'C'),
    ]
    await reorderWithinParent(null, [3, 2, 1])
    /// 乐观更新已应用 → API 失败 → refresh() 拉回
    expect(treeMock).toHaveBeenCalledTimes(1)
    /// 等 refresh 内部 await 完成
    await new Promise(r => setTimeout(r, 0))
    expect(rootIds(tree.value)).toEqual([1, 2, 3])
  })

  it('orderedIds 为空 → no-op，不调 API', async () => {
    reorderMock.mockReset()
    const { tree, reorderWithinParent } = useCategoryTree()
    tree.value = [makeNode(1, null, 'A')]
    await reorderWithinParent(null, [])
    expect(reorderMock).not.toHaveBeenCalled()
  })

  it('parentId 在树中找不到 → no-op，不调 API', async () => {
    reorderMock.mockReset()
    const { tree, reorderWithinParent } = useCategoryTree()
    tree.value = [makeNode(1, null, 'A')]
    await reorderWithinParent(999, [1])
    expect(reorderMock).not.toHaveBeenCalled()
  })

  it('orderedIds 缺失某些兄弟时保留原位（防御性）', async () => {
    reorderMock.mockReset()
    reorderMock.mockResolvedValue({ code: 'Ok', data: null, message: null })

    const { tree, reorderWithinParent } = useCategoryTree()
    tree.value = [
      makeNode(1, null, 'A'),
      makeNode(2, null, 'B'),
      makeNode(3, null, 'C'),
      makeNode(4, null, 'D'),
    ]
    /// 传入的 orderedIds 故意只包含部分兄弟 → 缺失的应该按原位补回
    await reorderWithinParent(null, [2, 1])
    expect(rootIds(tree.value)).toEqual([2, 1, 3, 4])
  })
})
