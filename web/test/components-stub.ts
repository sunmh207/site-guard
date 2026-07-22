/// `#components` auto-import 占位模块。
///
/// Nuxt 在构建期扫描 components 目录 + @nuxt/ui 插件，生成 `#components` 虚拟模块，
/// 把 UButton / UInput 等自动注册为局部组件。vitest happy-dom 下无法生成该虚拟模块，
/// SFC 里 `import { UButton } from '#components'` 会在 vite import-analysis 阶段报错。
///
/// 这个 stub 在 vitest resolve.alias 里取代 `#components`。为了让 SFC 里显式
/// `import { UButton } from '#components'` 拿到的组件能正常渲染（即使测试文件
/// 没有通过 global.stubs 把它覆盖掉），这里导出带最小模板的占位组件。
/// 测试文件通常会通过 global.stubs 把 UButton 覆盖成自己的那份，此时本模块的
/// 导出不会被使用；一旦没覆盖，这份也能让组件实例化不报错。
export const UButton = {
  props: ['label', 'disabled', 'icon'],
  template: '<button type="button" :disabled="disabled">{{ label }}<slot /></button>',
} as unknown
