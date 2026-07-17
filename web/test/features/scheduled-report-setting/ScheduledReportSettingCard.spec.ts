/// ScheduledReportSettingCard 组件测试。
///
/// 验证三种状态：
///   - 默认关闭（404）：cfg=null，USwitch off，时间默认 "08:00"
///   - 已开启：USwitch on + 出现发送时刻选择器
///   - 开关切换触发 setConfig；保存失败回滚到原值
///
/// 测试约定（与 OpenDashboardSettingCard.spec.ts 保持一致）：
///   - useMessage 全局 stub
///   - scheduledReportSettingApi 整模块 mock
///   - @nuxt/ui 组件用 global.stubs 简化
///   - 业务方法通过 vm 直接调用，绕开 USwitch stub click
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
const setConfigMock = vi.fn()
const deleteConfigMock = vi.fn()

vi.mock('~/features/scheduled-report-setting/api/scheduled-report-setting.api', () => ({
  scheduledReportSettingApi: {
    getConfig: (...args: unknown[]) => getConfigMock(...args),
    setConfig: (...args: unknown[]) => setConfigMock(...args),
    deleteConfig: (...args: unknown[]) => deleteConfigMock(...args),
  },
}))

import ScheduledReportSettingCard from '~/features/scheduled-report-setting/components/ScheduledReportSettingCard.vue'

const stubs = {
  UCard: { template: '<div><slot name="header" /><slot /></div>' },
  UBadge: { template: '<span><slot /></span>' },
  USwitch: { props: ['modelValue'], template: '<button :data-on="modelValue" @click="$emit(\'update:model-value\', !modelValue)"><slot /></button>' },
  UInput: { template: '<input />' },
}

const error404 = Object.assign(new Error('NOT_FOUND'), { data: { code: 'NOT_FOUND' } })

function resolvedConfig(enabled: boolean, time = '08:00') {
  return {
    code: '0',
    message: '',
    data: { key: 'scheduled_report', value: { enabled, time }, updatedAt: 1700000000000 },
  }
}

describe('ScheduledReportSettingCard', () => {
  it('默认关闭（404）：cfg=null，USwitch off，时间默认 08:00', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockRejectedValueOnce(error404)

    const w = mount(ScheduledReportSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect((w.vm as any).cfg).toBeNull()
    expect((w.vm as any).displayEnabled).toBe(false)
    expect((w.vm as any).displayTime).toBe('08:00')
    expect(w.text()).toContain('定时报告')
  })

  it('已开启时回显 enabled=true + 自定义时刻', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockResolvedValueOnce(resolvedConfig(true, '09:30'))

    const w = mount(ScheduledReportSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect((w.vm as any).displayEnabled).toBe(true)
    expect((w.vm as any).displayTime).toBe('09:30')
  })

  it('开启开关触发 setConfig({ enabled: true })', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockRejectedValueOnce(error404)
    setConfigMock.mockResolvedValueOnce(resolvedConfig(true))

    const w = mount(ScheduledReportSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    await (w.vm as any).save({ enabled: true })
    await w.vm.$nextTick()

    expect(setConfigMock).toHaveBeenCalledTimes(1)
    expect(setConfigMock).toHaveBeenCalledWith({ enabled: true, time: '08:00' })
  })

  it('保存失败 → enabled 回滚到原值', async () => {
    getConfigMock.mockReset()
    setConfigMock.mockReset()
    getConfigMock.mockResolvedValueOnce(resolvedConfig(true))
    setConfigMock.mockRejectedValueOnce(new Error('save failed'))

    const w = mount(ScheduledReportSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect((w.vm as any).displayEnabled).toBe(true)
    await (w.vm as any).save({ enabled: false })
    await w.vm.$nextTick()

    /// 失败回滚：displayEnabled 仍回到 true
    expect((w.vm as any).displayEnabled).toBe(true)
  })
})
