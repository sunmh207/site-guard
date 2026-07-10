/// NotificationSettingCard 组件测试。
///
/// 验证三种状态切换：
///   - 未配置态（getConfig 抛 404）：渲染空表单
///   - 已配置态：只读展示 + 「已设置」徽标
///   - 编辑态：进入编辑表单 + 测试/取消/保存按钮
///
/// 关键流程：
///   - save → updateConfig 被调到，cfg 切换到返回的 value
///   - remove → deleteConfig 被调到，cfg 被清空
///   - load 把后端值同步进 cfg / form
///
/// 测试约定（与 CategoryEditSlideover.spec.ts 保持一致）：
///   - useMessage / watch 在全局 stub
///   - notificationSettingApi 整 mock
///   - @nuxt/ui 组件用 global.stubs 简化
///   - 业务方法通过 vm 直接调用，绕开 UButton stub click
///   - mask 模块整 mock，避免 H1 顺序耦合；测试关心 masking 函数被调到即可
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
/// useToast 是 useMessage 的底层依赖（@nuxt/ui auto-import），
/// 测试环境没真实运行时，给个最小 mock：add 返回 id，update/remove/clear no-op。
;(globalThis as any).useToast = () => ({
  add: (_opts: any) => 'toast-1',
  update: noop,
  remove: noop,
  clear: noop,
})

import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'

/// notificationSettingApi 整模块 mock；下面四个 mock 通过 vi.fn 暴露给测试断言。
const getConfigMock = vi.fn()
const updateConfigMock = vi.fn()
const deleteConfigMock = vi.fn()
const testWebhookMock = vi.fn()

vi.mock('~/features/notification-setting/api/notification-setting.api', () => ({
  notificationSettingApi: {
    getConfig: (...args: unknown[]) => getConfigMock(...args),
    updateConfig: (...args: unknown[]) => updateConfigMock(...args),
    deleteConfig: (...args: unknown[]) => deleteConfigMock(...args),
    testWebhook: (...args: unknown[]) => testWebhookMock(...args),
  },
}))

/// maskWebhook 整模块 mock：组件只用到 maskWebhook 一个导出，给个固定返回值便于断言。
const maskWebhookMock = vi.fn(() => 'https://****')
vi.mock('~/shared/utils/mask', () => ({
  maskWebhook: (...args: unknown[]) => maskWebhookMock(...args),
}))

import NotificationSettingCard from '~/features/notification-setting/components/NotificationSettingCard.vue'

/// Nuxt UI 组件替换为简单 stub：true 时 Vue Test Utils 用占位元素渲染同名组件，
/// 但 UCard 需要展开 slot 才能拿到模板里的文本，所以用一个小组件模板。
/// Popconfirm 是项目内自定义组件（shared/components/Popconfirm.vue），运行时按 auto-import 解析，
/// 在测试环境也要 stub。
const stubs = {
  /// UButton / UCard / UBadge / Popconfirm 都包了一两层 slot，
  /// 用 `true` 时 Vue Test Utils 只渲染空占位，slot 内容被吃掉；
  /// 给最小组件模板展开 slot，使 wrapper.text() 能拿到中文文本。
  UButton: { template: '<button><slot /></button>' },
  UInput: true,
  /// UFormField 暴露 label，否则「平台」「Webhook URL」等表单 label 文本被吞，
  /// wrapper.text() 拿不到，断言不到表单是否展开。
  UFormField: { props: ['label'], template: '<div><label>{{ label }}</label><slot /></div>' },
  USelect: true,
  USwitch: true,
  UBadge: { template: '<span><slot /></span>' },
  UIcon: true,
  UTooltip: { template: '<span><slot /></span>' },
  Popconfirm: { template: '<span><slot /></span>' },
  UCard: { template: '<div><slot name="header" /><slot /></div>' },
}

/// 后端 404 的错误结构：notifySettingApi.getConfig 在 404 时抛 e（含 data.code === 'NOT_FOUND'）。
const error404 = Object.assign(new Error('NOT_FOUND'), { data: { code: 'NOT_FOUND' } })

/// 创建一份已配置态的 mock 响应，避免每个测试重复拼装。
///
/// 后端返回的是 StatusResult<ConfigResponse>（{ code, data, message } 包装）；
/// 组件必须走 res.data.value 读取，这里按真实线缆格式拼装，避免历史上
/// 「mock 漏掉 StatusResult 包装、组件误读 res.value」之类的回归。
function resolvedConfig(overrides: Partial<{
  enabled: boolean
  platform: 'DINGTALK' | 'WECHAT_WORK' | 'FEISHU'
  webhookUrl: string
  secret: string
}> = {}) {
  const value = {
    enabled: true,
    platform: 'DINGTALK' as const,
    webhookUrl: 'https://oapi.dingtalk.com/robot/send?access_token=abcdefghij',
    secret: 'sec123456',
    ...overrides,
  }
  return {
    code: 'Ok',
    message: null,
    data: { key: 'notification', value, updatedAt: 1700000000000 },
  }
}

describe('NotificationSettingCard', () => {
  it('未配置态（默认收起）：仅渲染引导 + 「设置」/「立即设置」，不展开表单字段', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockRejectedValue(error404)

    const w = mount(NotificationSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    /// 标题 + 「设置」按钮（header 内）+ 引导词 + 「立即设置」锚链接（主体内）
    expect(w.text()).toContain('通知设置')
    expect(w.text()).toContain('设置')
    expect(w.text()).toContain('未配置通知通道')
    expect(w.text()).toContain('立即设置')
    /// 已设置徽标、编辑/删除按钮不应出现
    expect(w.text()).not.toContain('已设置')
    expect(w.text()).not.toContain('编辑')
    expect(w.text()).not.toContain('删除')
    /// 表单区不渲染：「平台」「Webhook URL」label、「设置通知」标题不出现
    expect(w.text()).not.toContain('设置通知')
    expect(w.text()).not.toContain('平台')
    expect(w.text()).not.toContain('Webhook URL')
    expect(w.text()).not.toContain('签名密钥')
    expect(w.text()).not.toContain('保存')
    expect(w.text()).not.toContain('测试连接')
    /// 状态：cfg=null，expanded=false
    expect((w.vm as any).cfg).toBeNull()
    expect((w.vm as any).expanded).toBe(false)
  })

  it('未配置态：点击「设置」或「立即设置」展开表单，再点「取消」收回', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockRejectedValue(error404)

    const w = mount(NotificationSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    /// 默认收起
    expect((w.vm as any).expanded).toBe(false)

    /// 调 expandForm（模拟点击「设置」/「立即设置」）
    await (w.vm as any).expandForm()
    await w.vm.$nextTick()

    expect((w.vm as any).expanded).toBe(true)
    expect(w.text()).toContain('设置通知')
    expect(w.text()).toContain('Webhook URL')
    expect(w.text()).toContain('保存')
    /// header 里出现「取消」
    expect(w.text()).toContain('取消')

    /// 调 collapseForm 收回
    await (w.vm as any).collapseForm()
    await w.vm.$nextTick()

    expect((w.vm as any).expanded).toBe(false)
    expect(w.text()).not.toContain('设置通知')
    expect(w.text()).not.toContain('Webhook URL')
    /// 表单收回时 form 也被重置，避免下次展开残留输入
    expect((w.vm as any).form.webhookUrl).toBe('')
  })

  it('已配置态：不渲染未配置引导提示与「设置」按钮', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockResolvedValue(resolvedConfig({ webhookUrl: 'https://example.com/hook' }))

    const w = mount(NotificationSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect(w.text()).not.toContain('未配置通知通道')
    expect(w.text()).not.toContain('立即设置')
    /// header「设置」按钮只在未配置态显示；已配置态无此按钮
    /// 用 testid 精确断言（标题「通知设置」本身含「设置」字眼，不能直接 toContain('设置')）
    expect(w.find('[data-testid="notification-setup-btn"]').exists()).toBe(false)
    expect(w.find('[data-testid="notification-empty-hint"]').exists()).toBe(false)
  })

  it('已配置态：显示「已设置」徽标 + 平台中文标签', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockResolvedValue(resolvedConfig({ webhookUrl: 'https://example.com/hook' }))

    const w = mount(NotificationSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect(w.text()).toContain('已设置')
    expect(w.text()).toContain('钉钉')
    expect(w.text()).toContain('编辑')
    expect(w.text()).toContain('删除')
    expect((w.vm as any).cfg).not.toBeNull()
    expect((w.vm as any).cfg.platform).toBe('DINGTALK')
  })

  it('点击编辑切到编辑态：表单预填当前 cfg', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockResolvedValue(resolvedConfig({ webhookUrl: 'https://example.com/hook' }))

    const w = mount(NotificationSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    await (w.vm as any).startEdit()
    await w.vm.$nextTick()

    expect(w.text()).toContain('编辑通知')
    /// form 应回填 cfg 当前值
    expect((w.vm as any).form.platform).toBe('DINGTALK')
    expect((w.vm as any).form.webhookUrl).toBe('https://example.com/hook')
    expect((w.vm as any).editing).toBe(true)
  })

  it('点取消恢复非编辑态', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockResolvedValue(resolvedConfig())

    const w = mount(NotificationSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    await (w.vm as any).startEdit()
    await w.vm.$nextTick()
    expect((w.vm as any).editing).toBe(true)

    await (w.vm as any).cancelEdit()
    await w.vm.$nextTick()
    expect((w.vm as any).editing).toBe(false)
  })

  it('保存：调 updateConfig，成功后 cfg 切到返回的 value，并退出编辑态', async () => {
    getConfigMock.mockReset()
    updateConfigMock.mockReset()
    /// 第一次 getConfig 是 onMounted（→ 已配置态）
    getConfigMock.mockResolvedValueOnce(resolvedConfig({ webhookUrl: 'https://old.example.com/hook' }))
    /// 第二次 getConfig 是 save 内部的 reload
    getConfigMock.mockResolvedValueOnce(resolvedConfig({ webhookUrl: 'https://new.example.com/hook' }))
    updateConfigMock.mockResolvedValue({})

    const w = mount(NotificationSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    await (w.vm as any).startEdit()
    await w.vm.$nextTick()
    ;(w.vm as any).form.webhookUrl = 'https://new.example.com/hook'
    await (w.vm as any).save()
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect(updateConfigMock).toHaveBeenCalledTimes(1)
    expect(updateConfigMock).toHaveBeenCalledWith(expect.objectContaining({
      platform: 'DINGTALK',
      webhookUrl: 'https://new.example.com/hook',
    }))
    expect((w.vm as any).cfg).not.toBeNull()
    expect((w.vm as any).cfg.webhookUrl).toBe('https://new.example.com/hook')
    expect((w.vm as any).editing).toBe(false)
  })

  it('保存：未配置态展开后保存（创建配置），expanded 自动归零', async () => {
    getConfigMock.mockReset()
    updateConfigMock.mockReset()
    /// onMounted 时 404 → 未配置态
    getConfigMock.mockRejectedValueOnce(error404)
    /// save 内部 reload：此时已经有配置了
    getConfigMock.mockResolvedValueOnce(resolvedConfig({ webhookUrl: 'https://oapi.dingtalk.com/robot/send?access_token=xyz' }))
    updateConfigMock.mockResolvedValue({})

    const w = mount(NotificationSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect((w.vm as any).cfg).toBeNull()
    /// 未配置态默认收起，先展开才能拿到 form 字段
    await (w.vm as any).expandForm()
    expect((w.vm as any).expanded).toBe(true)

    ;(w.vm as any).form.webhookUrl = 'https://oapi.dingtalk.com/robot/send?access_token=xyz'
    await (w.vm as any).save()
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect(updateConfigMock).toHaveBeenCalledTimes(1)
    expect(updateConfigMock).toHaveBeenCalledWith(expect.objectContaining({
      webhookUrl: 'https://oapi.dingtalk.com/robot/send?access_token=xyz',
    }))
    expect((w.vm as any).cfg).not.toBeNull()
    expect((w.vm as any).cfg.webhookUrl).toBe('https://oapi.dingtalk.com/robot/send?access_token=xyz')
    /// 保存成功 → 切到「已配置 + 只读」；expanded 归零（避免下次回到未配置态直接展开）
    expect((w.vm as any).expanded).toBe(false)
  })

  it('删除：调 deleteConfig，cfg 被清空，回到「未配置 + 收起」', async () => {
    getConfigMock.mockReset()
    deleteConfigMock.mockReset()
    getConfigMock.mockResolvedValue(resolvedConfig())
    deleteConfigMock.mockResolvedValue({})

    const w = mount(NotificationSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect((w.vm as any).cfg).not.toBeNull()

    await (w.vm as any).remove()
    await w.vm.$nextTick()

    expect(deleteConfigMock).toHaveBeenCalledTimes(1)
    expect((w.vm as any).cfg).toBeNull()
    /// 删除后应回到「未配置 + 收起」，引导提示与「设置」按钮重新出现
    expect((w.vm as any).expanded).toBe(false)
    expect(w.text()).toContain('未配置通知通道')
    expect(w.text()).toContain('立即设置')
  })

  it('load：把后端返回的 value 写到 cfg 和 form', async () => {
    getConfigMock.mockReset()
    getConfigMock.mockResolvedValue(resolvedConfig({ platform: 'FEISHU' }))

    const w = mount(NotificationSettingCard, { global: { stubs } })
    await w.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()

    expect(getConfigMock).toHaveBeenCalledTimes(1)
    expect((w.vm as any).cfg.platform).toBe('FEISHU')
    expect((w.vm as any).form.platform).toBe('FEISHU')
  })
})
