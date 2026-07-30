import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { uiStubs } from '~/test/ui-stubs'

const api = vi.hoisted(() => ({
  getAdmin: vi.fn(),
  set: vi.fn(),
  deleteIcon: vi.fn(),
}))
const applyMock = vi.hoisted(() => vi.fn())
const toast = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  info: vi.fn(),
  warning: vi.fn(),
}))

vi.mock('../api/branding.api', () => ({ brandingApi: api }))
vi.mock('~/shared/composables/useMessage', () => ({ useMessage: () => toast }))
vi.stubGlobal('useBranding', () => ({ apply: applyMock }))
vi.stubGlobal('useMessage', () => toast)
vi.stubGlobal('useToast', () => ({ add: vi.fn(), update: vi.fn(), remove: vi.fn(), clear: vi.fn() }))

import BrandingSettingCard from './BrandingSettingCard.vue'

const initial = { name: 'Site Guard', iconUrl: '/favicon.ico', customIcon: false }
const custom = { name: 'Acme Monitor', iconUrl: '/branding/icon?v=2', customIcon: true }

function fileEvent(file: File) {
  return { target: { files: [file], value: 'selected' } } as unknown as Event
}

describe('BrandingSettingCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.getAdmin.mockResolvedValue({ data: initial })
    api.set.mockResolvedValue({ data: custom })
    api.deleteIcon.mockResolvedValue({ data: { ...custom, iconUrl: '/favicon.ico', customIcon: false } })
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:preview')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  })

  it('保存构造 FormData、读取 res.data 并 apply', async () => {
    const wrapper = mount(BrandingSettingCard, { global: { components: uiStubs } })
    await flushPromises()
    const file = new File(['png'], 'logo.png', { type: 'image/png' })
    ;(wrapper.vm as any).name = '  Acme Monitor  '
    ;(wrapper.vm as any).onIconChange(fileEvent(file))

    await (wrapper.vm as any).save()

    const body = api.set.mock.calls[0][0] as FormData
    expect(body.get('siteName')).toBe('Acme Monitor')
    expect(body.has('name')).toBe(false)
    const uploaded = body.get('icon') as File
    expect(uploaded).toBeInstanceOf(File)
    expect(uploaded.name).toBe('logo.png')
    expect(uploaded.type).toBe('image/png')
    expect(applyMock).toHaveBeenCalledWith(custom)
    expect((wrapper.vm as any).current).toEqual(custom)
  })

  it.each([
    [new File(['gif'], 'logo.gif', { type: 'image/gif' }), '图标仅支持 PNG 或 JPEG 格式'],
    [new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'huge.png', { type: 'image/png' }), '图标大小不能超过 2 MiB'],
  ])('拒绝不合法图片且不生成预览', async (file, error) => {
    const wrapper = mount(BrandingSettingCard, { global: { components: uiStubs } })
    await flushPromises()

    ;(wrapper.vm as any).onIconChange(fileEvent(file))

    expect(toast.error).toHaveBeenCalledWith(error)
    expect(URL.createObjectURL).not.toHaveBeenCalled()
    expect((wrapper.vm as any).iconFile).toBeNull()
  })

  it('替换预览与卸载都会 revoke blob URL', async () => {
    const wrapper = mount(BrandingSettingCard, { global: { components: uiStubs } })
    await flushPromises()
    const first = new File(['1'], 'one.png', { type: 'image/png' })
    const second = new File(['2'], 'two.jpg', { type: 'image/jpeg' })

    ;(wrapper.vm as any).onIconChange(fileEvent(first))
    ;(wrapper.vm as any).onIconChange(fileEvent(second))
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview')

    wrapper.unmount()
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2)
  })

  it('删除自定义图标读取 res.data 并 apply', async () => {
    api.getAdmin.mockResolvedValue({ data: custom })
    const wrapper = mount(BrandingSettingCard, { global: { components: uiStubs } })
    await flushPromises()

    await (wrapper.vm as any).deleteIcon()

    expect(api.deleteIcon).toHaveBeenCalledTimes(1)
    expect(applyMock).toHaveBeenCalledWith({ ...custom, iconUrl: '/favicon.ico', customIcon: false })
  })
})
