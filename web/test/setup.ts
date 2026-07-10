/// Vitest 全局 setup。
///
/// 在不启动完整 Nuxt 上下文的前提下为单元测试提供最小可用的 Vue 响应式能力。
/// 真正的 Nuxt 组件测试请走 @nuxt/test-utils 的 mountSuspended；
/// 对于轻量展示组件，可用 ~/test/ui-stubs 注册的 @nuxt/ui 桩组件保持 wrapper.text() 可见。
import { computed, ref, reactive } from 'vue'

;(globalThis as any).computed = computed
;(globalThis as any).ref = ref
;(globalThis as any).reactive = reactive
