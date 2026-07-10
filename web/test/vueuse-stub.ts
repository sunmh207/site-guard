/// vitest 环境下的 @vueuse/core 占位。
///
/// 真实包在 pnpm 严格布局下 vite import-analysis 阶段找不到，
/// 且 vi.mock 对外部包在 SFC transform 后的二级 import 拦截不可靠。
/// 这里用 resolve.alias 直接指向一个最小 stub，让 import-analysis 通过。
/// 各测试用例需要更精细控制时（如 advanceTimers 句柄），再在测试文件内用
/// vi.mock 替换这个 stub 模块。
import { vi } from 'vitest'
import { ref } from 'vue'

export const useIntervalFn = (_fn: () => void, _ms: number) => {
  return { pause: vi.fn(), resume: vi.fn() }
}

export const useWakeLock = () => ({
  isSupported: { value: false },
  request: vi.fn(),
  release: vi.fn(),
})

/// 浏览器原生全屏 stub。_target 在测试里不消费——用例通过 vi.mock 替换此实现，
/// 用受控 ref 验证 UI 响应。isSupported 默认 true，isFullscreen 默认 false。
export const useFullscreen = (_target?: unknown) => ({
  isSupported: ref(true),
  isFullscreen: ref(false),
  enter: vi.fn(),
  exit: vi.fn(),
  toggle: vi.fn(),
})