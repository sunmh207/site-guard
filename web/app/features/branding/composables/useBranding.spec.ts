import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { DEFAULT_BRANDING, type BrandingDto } from '../types/branding.dto'

const getPublicMock = vi.fn()
const states = new Map<string, ReturnType<typeof ref>>()

vi.mock('../api/branding.api', () => ({
  brandingApi: {
    getPublic: (...args: unknown[]) => getPublicMock(...args),
  },
}))

vi.stubGlobal('useState', <T>(key: string, init: () => T) => {
  if (!states.has(key)) states.set(key, ref(init()))
  return states.get(key)
})

async function loadFreshComposable() {
  vi.resetModules()
  return await import('./useBranding')
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

describe('useBranding', () => {
  beforeEach(() => {
    states.clear()
    getPublicMock.mockReset()
  })

  it('同一应用内并发 ensureLoaded 复用一个公开请求', async () => {
    const pending = deferred<{ data: BrandingDto }>()
    getPublicMock.mockReturnValue(pending.promise)
    const { useBranding } = await loadFreshComposable()
    const first = useBranding()
    const second = useBranding()

    const p1 = first.ensureLoaded()
    const p2 = second.ensureLoaded()
    expect(getPublicMock).toHaveBeenCalledTimes(1)

    pending.resolve({ data: { name: 'Acme', iconUrl: '/brand.png', customIcon: true } })
    await Promise.all([p1, p2])

    expect(first.data.value).toEqual({ name: 'Acme', iconUrl: '/brand.png', customIcon: true })
    expect(second.loaded.value).toBe(true)
  })

  it('公开读取失败静默保留默认值，并允许后续重试', async () => {
    getPublicMock
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ data: { name: '恢复', iconUrl: '/new.png', customIcon: true } })
    const { useBranding } = await loadFreshComposable()
    const branding = useBranding()

    await expect(branding.ensureLoaded()).resolves.toBeUndefined()
    expect(branding.data.value).toEqual(DEFAULT_BRANDING)
    expect(branding.loaded.value).toBe(false)

    await branding.ensureLoaded()
    expect(getPublicMock).toHaveBeenCalledTimes(2)
    expect(branding.data.value.name).toBe('恢复')
  })

  it('晚到的公开 load 不会覆盖刚 apply 的保存结果', async () => {
    const pending = deferred<{ data: BrandingDto }>()
    getPublicMock.mockReturnValue(pending.promise)
    const { useBranding } = await loadFreshComposable()
    const branding = useBranding()

    const load = branding.refresh()
    branding.apply({ name: '刚保存', iconUrl: '/saved.png', customIcon: true })
    pending.resolve({ data: { name: '旧响应', iconUrl: '/old.png', customIcon: true } })
    await load

    expect(branding.data.value).toEqual({ name: '刚保存', iconUrl: '/saved.png', customIcon: true })
    expect(branding.loaded.value).toBe(true)
    expect(branding.loading.value).toBe(false)
  })
})
