/// 公开大屏异常声音告警。
///
/// - 用户点击按钮后解锁 AudioContext（浏览器要求用户手势才能播放声音）
/// - 开启后：abnormalCount > 0 → 持续鸣响；= 0 → 立即停止
/// - 蜂鸣用 Web Audio API 合成双频警笛，无需音频文件
/// - 页面离开时自动清理
import type { Ref } from 'vue'
import { watch, onMounted, onScopeDispose, getCurrentInstance } from 'vue'

interface UseAlarmSoundOptions {
  /// 异常站点数，来自 dashboard data.summary.abnormalCount
  abnormalCount: Ref<number>
}

interface UseAlarmSoundReturn {
  /// 用户是否已开启告警声音
  enabled: Ref<boolean>
  /// AudioContext 是否已通过用户手势解锁
  isUnlocked: Ref<boolean>
  /// 当前是否正在鸣响
  isPlaying: Ref<boolean>
  /// 切换告警开关（用户点击按钮调用，同时完成 AudioContext 解锁）
  toggle: () => Promise<void>
}

/// 双频警笛参数：两个频率交替，每半秒切换一次。
const HIGH_FREQ = 880
const LOW_FREQ = 660
const BEEP_INTERVAL_MS = 500

export function useAlarmSound(options: UseAlarmSoundOptions): UseAlarmSoundReturn {
  const { abnormalCount } = options

  const enabled = ref(false)
  const isUnlocked = ref(false)
  const isPlaying = ref(false)

  /// AudioContext 及其节点，懒创建（首次 toggle 时才实例化）。
  let audioCtx: AudioContext | null = null
  /// 当前振荡器，停止时断开。
  let oscillator: OscillatorNode | null = null
  /// 增益节点，用于淡入淡出防破音。
  let gainNode: GainNode | null = null
  /// 切换频率的定时器。
  let toggleTimer: ReturnType<typeof setInterval> | null = null
  /// 当前输出频率，交替切换。
  let currentFreq = HIGH_FREQ

  /// 停止鸣响并清理节点。
  function stopAlarm() {
    if (toggleTimer) {
      clearInterval(toggleTimer)
      toggleTimer = null
    }
    if (oscillator) {
      try {
        oscillator.stop()
      }
      catch {
        // 已停止的振荡器再次 stop 会抛错，忽略
      }
      oscillator.disconnect()
      oscillator = null
    }
    if (gainNode) {
      gainNode.disconnect()
      gainNode = null
    }
    isPlaying.value = false
  }

  /// 启动蜂鸣循环：创建振荡器，定时切换频率产生警笛效果。
  function startAlarm() {
    if (!audioCtx) return

    stopAlarm()

    gainNode = audioCtx.createGain()
    gainNode.gain.value = 0.0001 // 从极小值开始，淡入
    gainNode.connect(audioCtx.destination)

    oscillator = audioCtx.createOscillator()
    oscillator.type = 'square'
    oscillator.frequency.value = currentFreq
    oscillator.connect(gainNode)
    oscillator.start()

    // 淡入
    gainNode.gain.exponentialRampToValueAtTime(0.3, audioCtx.currentTime + 0.05)

    isPlaying.value = true

    /// 定时切换频率产生警笛交替声。每次切换先淡出再淡入，防破音。
    toggleTimer = setInterval(() => {
      if (!oscillator || !gainNode || !audioCtx) return
      currentFreq = currentFreq === HIGH_FREQ ? LOW_FREQ : HIGH_FREQ
      const now = audioCtx.currentTime
      gainNode.gain.exponentialRampToValueAtTime(0.0001, now + 0.05)
      oscillator.frequency.value = currentFreq
      gainNode.gain.exponentialRampToValueAtTime(0.3, now + 0.1)
    }, BEEP_INTERVAL_MS)
  }

  /// 监听异常数与开关状态，联动启停。
  watch([enabled, abnormalCount], ([isEnabled, count]) => {
    if (isEnabled && count > 0) {
      if (audioCtx && audioCtx.state === 'running') {
        startAlarm()
      }
      // 若 AudioContext 尚未解锁（首次 toggle 还未调用），等 toggle 处理
    }
    else {
      stopAlarm()
    }
  })

  /// 用户点击按钮：解锁 AudioContext + 切换开关。
  async function toggle() {
    try {
      if (!audioCtx) {
        const Ctor = window.AudioContext || (window as any).webkitAudioContext
        if (!Ctor) {
          const { useMessage } = await import('~/shared/composables/useMessage')
          useMessage().error('当前浏览器不支持声音告警')
          return
        }
        audioCtx = new Ctor()
      }

      /// 浏览器可能把 AudioContext 置于 suspended，需显式 resume。
      if (audioCtx.state === 'suspended') {
        await audioCtx.resume()
      }

      isUnlocked.value = true
      enabled.value = !enabled.value
    }
    catch {
      enabled.value = false
      const { useMessage } = await import('~/shared/composables/useMessage')
      useMessage().error('声音告警初始化失败，请检查浏览器权限')
    }
  }

  /// 切回标签页时 resume AudioContext（系统可能因后台挂起）。
  function onVisibilityChange() {
    if (document.visibilityState === 'visible' && audioCtx?.state === 'suspended') {
      audioCtx.resume()
    }
  }

  /// visibilitychange 监听：仅在组件实例上下文（onMounted 可用）下注册。
  /// 纯 effectScope 测试环境无活跃组件，跳过——测试不依赖此行为。
  if (getCurrentInstance()) {
    onMounted(() => {
      document.addEventListener('visibilitychange', onVisibilityChange)
    })
  }

  onScopeDispose(() => {
    document.removeEventListener('visibilitychange', onVisibilityChange)
    stopAlarm()
    if (audioCtx) {
      audioCtx.close()
      audioCtx = null
    }
  })

  return {
    enabled,
    isUnlocked,
    isPlaying,
    toggle,
  }
}
