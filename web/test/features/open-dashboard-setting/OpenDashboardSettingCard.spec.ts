/// OpenDashboardSettingCard 组件测试。
///
/// 验证三种状态：
///   - 默认关闭（404）：cfg=false，「已关闭」徽标；USwitch off
///   - 已开启：cfg=true，「已开启」徽标 + 出现可复制 URL
///   - 切换触发保存；保存失败回滚到原值
///
/// 测试约定（与 NotificationSettingCard.spec.ts 保持一致）：
///   - useMessage / useToast 全局 stub
///   - openDashboardSettingApi 整模块 mock
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

const getEnabledMock = vi.fn()
const setEnabledMock = vi.fn()
const deleteEnabledMock = vi.fn()

vi.mock('~/features/open-dashboard-setting/api/open-dashboard-setting.api', () => ({
  openDashboardSettingApi: {
    getEnabled: (...args: unknown[]) => getEnabledMock(...args),
    setEnabled: (...args: unknown[]) => setEnabledMock(...args),
    deleteEnabled: (...args: unknown[]) => deleteEnabledMock(...args),
  },
}))

import OpenDashboardSettingCard from '~/features/open-dashboard-setting/components/OpenDashboardSettingCard.vue'

const stubs = {
  UButton: { template: '<button><slot /></button>' },
  UInput: { template: '<input />' },
  USwitch: { props: ['modelValue'], template: '<button :data-on="modelValue" @click="$emit(\'update:model-value\', !modelValue)"><slot /></button>' },
  UCard: { template: '<div><slot name="header" /><slot /></div>' },
  UBadge: { template: '<span><slot /></span>' },
  UIcon: true,
  UTooltip: { template: '<span><slot /></span>' },
}

/// 后端 404 的错误结构：与 NotificationSettingCard 一样约定为 data.code === 'NOT_FOUND'。
const error404 = Object.assign(new Error('NOT_FOUND'), { data: { code: 'NOT_FOUND' } })

/// 已开启态的 mock 响应（按真实线缆 StatusResult 包装）。
function resolvedEnabled(enabled: boolean) {
  return {
    code: 'Ok',
    message: null,
    data: { key: 'open_dashboard', value: enabled, updatedAt: 1700000000000 },
  }
}

describe('OpenDashboardSettingCard', () => {
  it('默认关闭（404）：cfg=false，徽标「已关闭」', async () => {
    getEnabledMock.mockReset()
    getEnabledMock.mockRejectedValueOnce(error404)

    const w = mount(OpenDashboardSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect(w.text()).toContain('公开大屏')
    expect(w.text()).toContain('已关闭')
    expect((w.vm as any).cfg).toBe(false)
    expect((w.vm as any).display).toBe(false)
  })

  it('已开启：cfg=true，徽标「已开启」，展示可复制 URL', async () => {
    getEnabledMock.mockReset()
    getEnabledMock.mockResolvedValueOnce(resolvedEnabled(true))

    const w = mount(OpenDashboardSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect(w.text()).toContain('已开启')
    expect(w.text()).toContain('复制')
    expect((w.vm as any).cfg).toBe(true)
    expect((w.vm as any).display).toBe(true)
  })

  it('切换开关触发 setEnabled(true)，成功后 cfg 切到 true', async () => {
    getEnabledMock.mockReset()
    setEnabledMock.mockReset()
    /// onMounted 时 404 → 默认关闭
    getEnabledMock.mockRejectedValueOnce(error404)
    setEnabledMock.mockResolvedValueOnce({})

    const w = mount(OpenDashboardSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect((w.vm as any).cfg).toBe(false)
    /// 切到 true
    await (w.vm as any).save(true)
    await w.vm.$nextTick()

    expect(setEnabledMock).toHaveBeenCalledTimes(1)
    expect(setEnabledMock).toHaveBeenCalledWith(true)
    expect((w.vm as any).cfg).toBe(true)
    expect((w.vm as any).display).toBe(true)
  })

  it('关闭态下保存失败 → cfg 回滚到原值，UI 也回滚', async () => {
    getEnabledMock.mockReset()
    setEnabledMock.mockReset()
    /// onMounted 时已是 true（已开启）
    getEnabledMock.mockResolvedValueOnce(resolvedEnabled(true))
    /// 关闭保存失败
    setEnabledMock.mockRejectedValueOnce(new Error('save failed'))

    const w = mount(OpenDashboardSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect((w.vm as any).cfg).toBe(true)
    await (w.vm as any).save(false)
    await w.vm.$nextTick()

    /// 失败回滚：cfg 与 display 都回到 true
    expect(setEnabledMock).toHaveBeenCalledWith(false)
    expect((w.vm as any).cfg).toBe(true)
    expect((w.vm as any).display).toBe(true)
  })

  it('load 把后端 value 正确反映到 cfg / display', async () => {
    getEnabledMock.mockReset()
    getEnabledMock.mockResolvedValue(resolvedEnabled(true))

    const w = mount(OpenDashboardSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect(getEnabledMock).toHaveBeenCalledTimes(1)
    expect((w.vm as any).cfg).toBe(true)
    expect((w.vm as any).display).toBe(true)
  })
})
