/// AlertConfirmSettingCard 组件测试。
///
/// 验证三种状态切换：
///   - 未设置态（getConfig 返回 data:null）：显示默认值 1 + 「未设置」提示
///   - 已设置态：只读展示当前阈值 + 「编辑」按钮
///   - 编辑态：进入编辑表单，修改 draft + 调 save 后 value 写回
///
/// 测试约定（与 NotificationSettingCard.spec.ts / OpenDashboardSettingCard.spec.ts 保持一致）：
///   - alertConfirmSettingApi 整模块 mock
///   - @nuxt/ui 组件用 global.stubs 简化
///   - 业务方法通过 vm 直接调用，绕开 UButton stub click
import { watch } from 'vue'

/// Nuxt auto-import 占位：必须在 import 组件前挂到 globalThis
const noop = () => {}
;(globalThis as any).useMessage = () => ({
  success: noop,
  error: noop,
  info: noop,
  warning: noop,
})
;(globalThis as any).watch = watch
;(globalThis as any).useToast = () => ({
  add: (_opts: any) => 'toast-1',
  update: noop,
  remove: noop,
  clear: noop,
})

import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'

const getConfigMock = vi.fn()
const updateConfigMock = vi.fn()
const deleteConfigMock = vi.fn()

vi.mock('../api/alert-confirm-setting.api', () => ({
  alertConfirmSettingApi: {
    getConfig: (...args: unknown[]) => getConfigMock(...args),
    updateConfig: (...args: unknown[]) => updateConfigMock(...args),
    deleteConfig: (...args: unknown[]) => deleteConfigMock(...args),
  },
}))

import AlertConfirmSettingCard from './AlertConfirmSettingCard.vue'

const stubs = {
  UButton: { template: '<button><slot /></button>' },
  UInput: true,
  /// UFormField 在测试里需要把 label 渲染出来（编辑态断言"阈值 N"）；
  /// 用 `true` 时 slot 被吞掉，wrapper.text() 拿不到 label。
  UFormField: { props: ['label'], template: '<div><label>{{ label }}</label><slot /></div>' },
  UCard: { template: '<div><slot name="header" /><slot /></div>' },
}

/// 已配置态的 mock 响应（按真实线缆 StatusResult 包装）。
function resolvedConfig(threshold: number) {
  return {
    code: 'Ok',
    message: null,
    data: {
      key: 'consecutive_failures_before_alert',
      value: { consecutiveFailuresBeforeAlert: threshold },
      updatedAt: 1700000000000,
    },
  }
}

/// 未配置态（api 层 404 fallback 后）返回 {code:'0', data:null, message:''}。
function resolvedNull() {
  return { code: '0', data: null, message: '' }
}

describe('AlertConfirmSettingCard', () => {
  it('未设置态：显示默认值 1 + 「未设置」提示', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockResolvedValueOnce(resolvedNull())

    const w = mount(AlertConfirmSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect(w.text()).toContain('连续失败阈值')
    expect(w.text()).toContain('1')
    expect(w.text()).toContain('未设置')
    expect((w.vm as any).value).toBeNull()
    expect((w.vm as any).isSet).toBe(false)
  })

  it('已设置态：只读展示当前阈值 3，按钮文案「编辑」', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockResolvedValueOnce(resolvedConfig(3))

    const w = mount(AlertConfirmSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect(w.text()).toContain('3')
    expect(w.text()).toContain('编辑')
    expect(w.text()).not.toContain('未设置')
    expect((w.vm as any).value).toBe(3)
    expect((w.vm as any).isSet).toBe(true)
  })

  it('点击编辑切到编辑态：显示 UInput + 保存/取消按钮', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockResolvedValueOnce(resolvedConfig(3))

    const w = mount(AlertConfirmSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    await (w.vm as any).startEdit()
    await w.vm.$nextTick()

    expect(w.text()).toContain('保存')
    expect(w.text()).toContain('取消')
    expect(w.text()).toContain('阈值 N')
    expect((w.vm as any).editing).toBe(true)
    /// draft 预填当前 value
    expect((w.vm as any).draft).toBe(3)
  })

  it('点取消恢复非编辑态：draft 重置回 value', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockResolvedValueOnce(resolvedConfig(3))

    const w = mount(AlertConfirmSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    await (w.vm as any).startEdit()
    await w.vm.$nextTick()
    ;(w.vm as any).draft = 9
    expect((w.vm as any).editing).toBe(true)

    await (w.vm as any).cancel()
    await w.vm.$nextTick()
    expect((w.vm as any).editing).toBe(false)
    expect((w.vm as any).draft).toBe(3)
  })

  it('保存：调 updateConfig，成功后 value 写回并退出编辑态', async () => {
    getConfigMock.mockReset()
    updateConfigMock.mockReset()
    getConfigMock.mockResolvedValueOnce(resolvedConfig(3))
    updateConfigMock.mockResolvedValue({})

    const w = mount(AlertConfirmSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    await (w.vm as any).startEdit()
    await w.vm.$nextTick()
    ;(w.vm as any).draft = 7
    await (w.vm as any).save()
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect(updateConfigMock).toHaveBeenCalledTimes(1)
    expect(updateConfigMock).toHaveBeenCalledWith({ consecutiveFailuresBeforeAlert: 7 })
    expect((w.vm as any).value).toBe(7)
    expect((w.vm as any).editing).toBe(false)
  })

  it('保存：未设置态（value=null）也能直接保存（创建配置）', async () => {
    getConfigMock.mockReset()
    updateConfigMock.mockReset()
    getConfigMock.mockResolvedValueOnce(resolvedNull())
    updateConfigMock.mockResolvedValue({})

    const w = mount(AlertConfirmSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect((w.vm as any).value).toBeNull()

    await (w.vm as any).startEdit()
    await w.vm.$nextTick()
    ;(w.vm as any).draft = 4
    await (w.vm as any).save()
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect(updateConfigMock).toHaveBeenCalledTimes(1)
    expect(updateConfigMock).toHaveBeenCalledWith({ consecutiveFailuresBeforeAlert: 4 })
    expect((w.vm as any).value).toBe(4)
    expect((w.vm as any).editing).toBe(false)
  })

  it('保存：draft < 1 时 valibot 校验失败，不调 updateConfig，error 写入', async () => {
    getConfigMock.mockReset()
    updateConfigMock.mockReset()
    getConfigMock.mockResolvedValueOnce(resolvedConfig(3))

    const w = mount(AlertConfirmSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    await (w.vm as any).startEdit()
    await w.vm.$nextTick()
    ;(w.vm as any).draft = 0
    await (w.vm as any).save()
    await w.vm.$nextTick()

    expect(updateConfigMock).not.toHaveBeenCalled()
    expect((w.vm as any).error).toBe('最小值为 1')
    expect((w.vm as any).editing).toBe(true)
  })

  it('load：把后端 value 写到 value 和 draft', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockResolvedValue(resolvedConfig(5))

    const w = mount(AlertConfirmSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect(getConfigMock).toHaveBeenCalledTimes(1)
    expect((w.vm as any).value).toBe(5)
    expect((w.vm as any).draft).toBe(5)
  })
})
