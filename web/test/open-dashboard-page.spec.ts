/// /open/dashboard 公开大屏页面的单测。
///
/// mock 策略：
///   - vitest config alias 把 @vueuse/core 指向 test/vueuse-stub（pnpm 严格布局下
///     vite import-analysis 找不到真实包；vi.mock 对外部包在 SFC transform 后的
///     二级 import 拦截不可靠），stub 文件里 useIntervalFn 会同步调 advanceTimers()
///   - test 文件内用 vi.mock 再覆盖 stub，让 advanceTimers 句柄可控、useWakeLock
///     暴露 isSupported + request 以便断言
///   - stub openSiteStatsApi.getDashboard：返回受控的 data/refresh/status/error
///   - 用 uiStubs 替换 Nuxt UI 组件，避免依赖 Nuxt 上下文
///
/// mount 包装：页面用了 `await openSiteStatsApi.getDashboard()`，setup 是 async，
/// 需要外层 <Suspense> 才能 mount。定义一个 Harness 子组件把 OpenDashboard 包起来。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { uiStubs } from '~/test/ui-stubs'
import OpenDashboard from '~/pages/open/dashboard.vue'

/// Nuxt auto-import 占位：useMessage → useToast 链路。本页 dashboard.vue 用 useMessage
/// 处理全屏切换失败 toast，测试环境下不存在 Nuxt 全局上下文，必须在 import 组件前
/// 把它们挂到 globalThis（与 OpenDashboardSettingCard.spec.ts 同款约定）。
const noop = () => {}
;(globalThis as any).useMessage = () => ({
  success: noop,
  error: noop,
  info: noop,
  warning: noop,
})
;(globalThis as any).useToast = () => ({
  add: (_opts: any) => 'toast-1',
  update: noop,
  remove: noop,
  clear: noop,
})

/// advanceTimers 同时承担「setup 时记录 + 外部手动推进」两件事。
/// 测试调 advanceTimers() 时让它触发最近注册的 interval callback，
/// 从而让 doRefresh() 跑起来调 refresh mock。
const advanceTimers = vi.hoisted(() => {
  let pendingCb: (() => void) | null = null
  const fn = vi.fn(() => {
    if (pendingCb) pendingCb()
  })
  fn.setCallback = (cb: () => void) => { pendingCb = cb }
  return fn
})
const requestWakeLock = vi.hoisted(() => vi.fn())

/// 全屏 stub 的状态容器占位。ref 必须在 vi.mock 工厂内用 await import('vue')
/// 创建——vi.hoisted 比静态 import 更早执行，直接在 hoisted 里调 ref() 会撞到
/// "Cannot access before initialization"。工厂用 fullscreenState 容器把 ref 与
/// toggle 装好后回填到这里，测试用例通过 .value 直接改 ref 触发 Vue 响应式。
const fullscreenState = vi.hoisted(() => ({
  isSupported: null as any,
  isFullscreen: null as any,
  toggle: vi.fn(),
}))

vi.mock('@vueuse/core', async () => {
  const { ref } = await import('vue')
  /// 同一个 ref / toggle 跨测试共享，vi.clearAllMocks 不动 ref 值，所以 beforeEach
  /// 还要把 isFullscreen.value 显式归零。
  fullscreenState.isSupported = ref(true)
  fullscreenState.isFullscreen = ref(false)
  fullscreenState.toggle = vi.fn(async () => {
    if (fullscreenState.isFullscreen) {
      fullscreenState.isFullscreen.value = !fullscreenState.isFullscreen.value
    }
  })
  return {
    useIntervalFn: (cb: () => void, _ms: number) => {
      advanceTimers.setCallback(cb)
      return { pause: vi.fn(), resume: vi.fn() }
    },
    useWakeLock: () => ({
      isSupported: { value: true },
      request: requestWakeLock,
      release: vi.fn(),
    }),
    useFullscreen: () => ({
      isSupported: fullscreenState.isSupported,
      isFullscreen: fullscreenState.isFullscreen,
      enter: vi.fn(),
      exit: vi.fn(),
      toggle: fullscreenState.toggle,
    }),
  }
})

/// 用 vi.hoisted 装一组可变状态容器。getDashboard mock 工厂在运行时拿到这个
/// 容器并把 data/error/status 装成 vue ref，模拟 useFetch 的真实行为：Vue
/// 会在模板里自动 unwrap ref。测试通过 apiState.data.value = ... 改值时，
/// 由于页面持有的就是同一个 ref，模板会同步刷新。
///
/// 这里只占位 data/error/status 三个字段为 null（mock 工厂会替换为真 ref）；
/// refresh 是一个独立 vi.fn。
const apiState = vi.hoisted(() => ({
  data: null as any,
  error: null as any,
  status: null as any,
  refresh: vi.fn(),
}))

vi.mock('~/features/site/api/open-stats.api', async () => {
  // mock 工厂运行时模块 import 已就绪，可以拿真实 ref()。
  // 第一次调用 getDashboard 时创建 refs 并写回 apiState，让测试用例通过
  // apiState.data.value = ... 改值，页面持有的同一个 ref 会响应式更新。
  const { ref } = await import('vue')
  apiState.data = ref<any>(null)
  apiState.error = ref<any>(null)
  apiState.status = ref<'success' | 'pending' | 'error' | 'idle'>('success')
  return {
    openSiteStatsApi: {
      getDashboard: () => ({
        data: apiState.data,
        refresh: apiState.refresh,
        status: apiState.status,
        error: apiState.error,
      }),
    },
  }
})

/// useColorMode 是 Nuxt auto-import；测试环境下用最小 stub
vi.stubGlobal('useColorMode', () => ({ preference: { value: 'dark' } }))

/// definePageMeta 是 Nuxt 编译时宏，运行时未定义；stub 为 no-op
vi.stubGlobal('definePageMeta', () => {})

/// 把 async-setup 的页面挂到 Suspense 下，否则 mount 会得到 "no <Suspense> boundary"
/// 警告并跳过渲染。Harness 作为父组件包 <Suspense><OpenDashboard /></Suspense>。
import { Suspense } from 'vue'
const Harness = defineComponent({
  setup() {
    return () => h('div', { class: 'harness' }, [
      h(Suspense, null, {
        default: () => h(OpenDashboard),
      }),
    ])
  },
})

describe('OpenDashboard 公开大屏页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiState.data.value = null
    apiState.error.value = null
    apiState.status.value = 'success'
    /// 全屏 stub 状态归位：vi.clearAllMocks 已清掉 toggle 调用记录，但仍需把
    /// isFullscreen ref 显式还原（ref 的值不走 mock 历史）。
    fullscreenState.isFullscreen.value = false
    fullscreenState.isSupported.value = true
  })

  it('加载成功 → 渲染摘要卡 + 告警 + 绝对时间戳', async () => {
    apiState.data.value = {
      summary: { totalSites: 12, healthyCount: 8, abnormalCount: 2, pendingCount: 1, pausedCount: 1, avgResponseMs: 100 },
      recentAlerts: [],
    }
    apiState.refresh.mockResolvedValue(undefined)
    apiState.status.value = 'success'

    const wrapper = mount(Harness, {
      global: { components: uiStubs },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('最后刷新')
    // 摘要卡与告警表是 stub 化的组件；不展开内部断言，只断言不出现错误角标
    expect(wrapper.text()).not.toContain('数据获取失败')
  })

  it('加载失败 → 渲染错误角标 + 不渲染卡片', async () => {
    apiState.data.value = null
    apiState.error.value = { statusCode: 500, message: 'fail' }
    apiState.status.value = 'error'

    const wrapper = mount(Harness, {
      global: { components: uiStubs },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('数据获取失败')
  })

  it('轮询推进 30s → 触发 refresh', async () => {
    apiState.data.value = {
      summary: { totalSites: 1, healthyCount: 1, abnormalCount: 0, pendingCount: 0, pausedCount: 0, avgResponseMs: null },
      recentAlerts: [],
    }
    apiState.refresh.mockResolvedValue(undefined)
    apiState.status.value = 'success'

    mount(Harness, { global: { components: uiStubs } })
    await flushPromises()

    const callsBefore = apiState.refresh.mock.calls.length
    advanceTimers()  // 模拟 useIntervalFn 触发
    await flushPromises()

    expect(apiState.refresh.mock.calls.length).toBeGreaterThan(callsBefore)
  })

  /// 验证 dark-mode 硬绑已移除：用户切到 light 时骨架能正常渲染（不依赖 dark 类）。
  /// 走 beforeEach 的 vi.clearAllMocks 之后，重新 stub useColorMode 返回 light。
  it('colorMode preference=light → 骨架正常渲染（暗色策略已解绑）', async () => {
    apiState.data.value = null
    apiState.error.value = null
    apiState.status.value = 'pending'

    vi.stubGlobal('useColorMode', () => ({ preference: { value: 'light' } }))

    const wrapper = mount(Harness, { global: { components: uiStubs } })
    await flushPromises()

    // 验证骨架组件确实挂载——data 为 null 时页面应渲染 <DashboardSkeleton />。
    // 比"不显示错误角标"更强：白屏 / v-if 分支误关 / 子组件未解析都会被抓到。
    // 注：ui-stubs.ts 里 DashboardSkeleton 是对象式 stub、没有 `name` 选项，
    // 所以不能用 findAllComponents({ name: 'DashboardSkeleton' })；改用 stub 暴露
    // 的 data-testid="dashboard-skeleton" 选择器，等价且稳定（与 DashboardSkeleton.spec.ts 同款）。
    expect(wrapper.findAll('[data-testid="dashboard-skeleton"]').length).toBe(1)
  })

  /// 验证 dark 路径仍然正常（向后兼容）。
  it('colorMode preference=dark → 骨架正常渲染（保持原行为）', async () => {
    apiState.data.value = null
    apiState.error.value = null
    apiState.status.value = 'pending'

    vi.stubGlobal('useColorMode', () => ({ preference: { value: 'dark' } }))

    const wrapper = mount(Harness, { global: { components: uiStubs } })
    await flushPromises()

    // 与 light 用例对齐：用 stub 暴露的 data-testid 断言骨架确实挂载。
    expect(wrapper.findAll('[data-testid="dashboard-skeleton"]').length).toBe(1)
  })

  /// 关闭态（admin 未开启）：后端返回 404 错误，前端应渲染「未开启」友好提示页。
  /// 这是本次产品设计的关键路径：把"隐式开放"显式化为"默认关闭"，并给出友好提示。
  ///
  /// 这里构造的 error 形状与 useApi 拦截器把 AppException 转换出的对象一致：
  /// { data: { code: 'NOT_FOUND', ... }, statusCode: 404 }。
  it('关闭态（后端 404）→ 渲染友好提示页，不渲染错误角标', async () => {
    apiState.data.value = null
    apiState.error.value = {
      data: { code: 'NOT_FOUND', message: '公开大屏未开启' },
      statusCode: 404,
    }
    apiState.status.value = 'error'

    const wrapper = mount(Harness, { global: { components: uiStubs } })
    await flushPromises()

    /// 「未开启」标题应可见
    expect(wrapper.text()).toContain('公开大屏未开启')
    /// 不应有「数据获取失败」角标（那是用于真实网络/服务端异常的）
    expect(wrapper.text()).not.toContain('数据获取失败')
    /// data-testid 让单测能稳定拿到关闭态 DOM 标识
    expect(wrapper.find('[data-testid="open-dashboard-disabled"]').exists()).toBe(true)
  })

  /// 「全屏」按钮的可见性条件：v-if 要求 data 已加载。data=null 时按钮不渲染，
  /// 避免骨架屏 / 错误态下用户对着无意义按钮点。
  it('data=null → 全屏按钮不渲染', async () => {
    apiState.data.value = null
    apiState.error.value = null
    apiState.status.value = 'pending'

    const wrapper = mount(Harness, { global: { components: uiStubs } })
    await flushPromises()

    expect(wrapper.find('[data-testid="open-dashboard-fullscreen-toggle"]').exists()).toBe(false)
  })

  /// 数据加载成功后按钮出现，初始状态文案/aria 与"未全屏"语义一致。
  it('数据加载成功 → 显示全屏按钮，初始状态为"全屏"', async () => {
    apiState.data.value = {
      summary: { totalSites: 1, healthyCount: 1, abnormalCount: 0, pendingCount: 0, pausedCount: 0, avgResponseMs: null },
      recentAlerts: [],
    }
    apiState.refresh.mockResolvedValue(undefined)
    apiState.status.value = 'success'

    const wrapper = mount(Harness, { global: { components: uiStubs } })
    await flushPromises()

    const btn = wrapper.find('[data-testid="open-dashboard-fullscreen-toggle"]')
    expect(btn.exists()).toBe(true)
    expect(btn.text()).toBe('全屏')
    expect(btn.attributes('aria-label')).toBe('进入全屏')
  })

  /// 点击按钮触发 toggle，stub 同步翻转 isFullscreen，UI 文案/aria 自动跟随更新。
  /// 验证点：(a) toggle 被调一次；(b) 状态翻转后按钮文案切到「退出全屏」。
  it('点击全屏按钮 → 触发 toggle，状态切换后文案/aria 跟随', async () => {
    apiState.data.value = {
      summary: { totalSites: 1, healthyCount: 1, abnormalCount: 0, pendingCount: 0, pausedCount: 0, avgResponseMs: null },
      recentAlerts: [],
    }
    apiState.refresh.mockResolvedValue(undefined)
    apiState.status.value = 'success'

    const wrapper = mount(Harness, { global: { components: uiStubs } })
    await flushPromises()

    const btn = wrapper.find('[data-testid="open-dashboard-fullscreen-toggle"]')
    expect(btn.exists()).toBe(true)
    expect(btn.text()).toBe('全屏')

    /// 模拟点击：toggle 是 async 函数，stub 内部翻转 isFullscreen。
    await btn.trigger('click')
    await flushPromises()

    expect(fullscreenState.toggle).toHaveBeenCalledTimes(1)
    expect(fullscreenState.isFullscreen.value).toBe(true)

    const btnAfter = wrapper.find('[data-testid="open-dashboard-fullscreen-toggle"]')
    expect(btnAfter.text()).toBe('退出全屏')
    expect(btnAfter.attributes('aria-label')).toBe('退出全屏')
  })
})