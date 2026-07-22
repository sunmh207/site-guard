/// useAlarmSound composable 单测。
///
/// mock 策略：
///   - AudioContext / OscillatorNode / GainNode 都不是浏览器原生，测试环境不存在；
///     用 vi.stubGlobal 提供最小 mock，记录 start/stop/frequency 调用。
///   - document.visibilitychange 监听用 vi.spyOn addEventListener。
///   - 用 effectScope 驱动 composable 的生命周期。
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { effectScope, ref } from 'vue'
import { useAlarmSound } from '~/shared/composables/useAlarmSound'

/// 记录振荡器行为，便于断言「是否正在鸣响」。
const oscillatorMock = vi.hoisted(() => ({
  starts: 0,
  stops: 0,
  lastFrequency: null as number | null,
}))

function createAudioContextMock() {
  return vi.fn(() => ({
    state: 'suspended',
    currentTime: 0,
    destination: {},
    resume: vi.fn(async () => {}),
    close: vi.fn(),
    createGain: vi.fn(() => ({
      gain: {
        value: 0,
        exponentialRampToValueAtTime: vi.fn(),
      },
      connect: vi.fn(),
      disconnect: vi.fn(),
    })),
    createOscillator: vi.fn(() => {
      const osc = {
        type: 'sine',
        frequency: { value: 0 },
        connect: vi.fn(),
        disconnect: vi.fn(),
        start: vi.fn(() => { oscillatorMock.starts++ }),
        stop: vi.fn(() => { oscillatorMock.stops++ }),
      }
      return osc
    }),
  }))
}

describe('useAlarmSound', () => {
  let scope: ReturnType<typeof effectScope>
  let audioCtxMock: ReturnType<typeof createAudioContextMock>

  beforeEach(() => {
    vi.useFakeTimers()
    oscillatorMock.starts = 0
    oscillatorMock.stops = 0
    oscillatorMock.lastFrequency = null

    audioCtxMock = createAudioContextMock()
    vi.stubGlobal('AudioContext', audioCtxMock)
    // webkit 前缀不应被使用（标准 AudioContext 已存在）
    vi.stubGlobal('webkitAudioContext', undefined)

    // visibilitychange 监听
    vi.spyOn(document, 'addEventListener')
    vi.spyOn(document, 'removeEventListener')
    Object.defineProperty(document, 'visibilityState', {
      value: 'visible',
      configurable: true,
    })

    scope = effectScope()
  })

  afterEach(() => {
    scope.stop()
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('初始状态：未开启、未解锁、未播放', () => {
    let ret: ReturnType<typeof useAlarmSound>
    scope.run(() => {
      ret = useAlarmSound({ abnormalCount: ref(0) })
    })
    expect(ret!.enabled.value).toBe(false)
    expect(ret!.isUnlocked.value).toBe(false)
    expect(ret!.isPlaying.value).toBe(false)
  })

  it('toggle → 解锁 AudioContext + 翻转 enabled', async () => {
    let ret: ReturnType<typeof useAlarmSound>
    scope.run(() => {
      ret = useAlarmSound({ abnormalCount: ref(0) })
    })

    await ret!.toggle()

    expect(ret!.isUnlocked.value).toBe(true)
    expect(ret!.enabled.value).toBe(true)
    expect(audioCtxMock).toHaveBeenCalledTimes(1)
  })

  it('toggle 两次 → 关闭告警', async () => {
    let ret: ReturnType<typeof useAlarmSound>
    scope.run(() => {
      ret = useAlarmSound({ abnormalCount: ref(0) })
    })

    await ret!.toggle()
    expect(ret!.enabled.value).toBe(true)
    await ret!.toggle()
    expect(ret!.enabled.value).toBe(false)
  })

  it('enabled=true 且 abnormalCount>0 → 开始鸣响', async () => {
    const count = ref(0)
    let ret: ReturnType<typeof useAlarmSound>
    scope.run(() => {
      ret = useAlarmSound({ abnormalCount: count })
    })

    await ret!.toggle()
    expect(ret!.isPlaying.value).toBe(false)

    // 模拟 AudioContext resume 后进入 running 状态
    const ctx = audioCtxMock.mock.results[0]!.value
    ctx.state = 'running'

    count.value = 2
    await vi.advanceTimersByTimeAsync(0)

    expect(ret!.isPlaying.value).toBe(true)
    expect(oscillatorMock.starts).toBe(1)
  })

  it('abnormalCount 归零 → 停止鸣响', async () => {
    const count = ref(0)
    let ret: ReturnType<typeof useAlarmSound>
    scope.run(() => {
      ret = useAlarmSound({ abnormalCount: count })
    })

    await ret!.toggle()
    const ctx = audioCtxMock.mock.results[0]!.value
    ctx.state = 'running'

    count.value = 1
    await vi.advanceTimersByTimeAsync(0)
    expect(ret!.isPlaying.value).toBe(true)

    count.value = 0
    await vi.advanceTimersByTimeAsync(0)
    expect(ret!.isPlaying.value).toBe(false)
    expect(oscillatorMock.stops).toBe(1)
  })

  it('enabled=false 时即使异常也不响', async () => {
    const count = ref(0)
    let ret: ReturnType<typeof useAlarmSound>
    scope.run(() => {
      ret = useAlarmSound({ abnormalCount: count })
    })

    // 不调用 toggle，保持 enabled=false
    count.value = 5
    await vi.advanceTimersByTimeAsync(0)

    expect(ret!.isPlaying.value).toBe(false)
    expect(oscillatorMock.starts).toBe(0)
  })

  it('scope 销毁 → 清理 AudioContext + 停止鸣响', async () => {
    const count = ref(0)
    let ret: ReturnType<typeof useAlarmSound>
    scope.run(() => {
      ret = useAlarmSound({ abnormalCount: count })
    })

    await ret!.toggle()
    const ctx = audioCtxMock.mock.results[0]!.value
    ctx.state = 'running'

    count.value = 1
    await vi.advanceTimersByTimeAsync(0)
    expect(ret!.isPlaying.value).toBe(true)

    scope.stop()
    expect(oscillatorMock.stops).toBeGreaterThanOrEqual(1)
    expect(ctx.close).toHaveBeenCalledTimes(1)
  })

  it('浏览器不支持 AudioContext → toast 错误 + 不崩溃', async () => {
    vi.stubGlobal('AudioContext', undefined)
    vi.stubGlobal('webkitAudioContext', undefined)

    const toastError = vi.fn()
    vi.stubGlobal('useMessage', () => ({
      success: vi.fn(),
      error: toastError,
      info: vi.fn(),
      warning: vi.fn(),
    }))
    vi.stubGlobal('useToast', () => ({
      add: vi.fn(),
      update: vi.fn(),
      remove: vi.fn(),
      clear: vi.fn(),
    }))

    let ret: ReturnType<typeof useAlarmSound>
    scope.run(() => {
      ret = useAlarmSound({ abnormalCount: ref(0) })
    })

    await ret!.toggle()
    expect(ret!.enabled.value).toBe(false)
    expect(ret!.isUnlocked.value).toBe(false)
  })
})
