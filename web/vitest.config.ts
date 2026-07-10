import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'

/// Vitest 配置：为 Nuxt 项目运行单元测试。
///
/// - environment happy-dom：避免需要启动真实浏览器
/// - setupFiles：注入最小 Nuxt auto-import 占位（computed/ref 等），让纯逻辑测试不必依赖完整 Nuxt 上下文
/// - include：覆盖 features 与 test 目录
/// - plugins.vue：注册 @vitejs/plugin-vue，让 vitest 能解析 import ... from '*.vue'
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'happy-dom',
    setupFiles: ['./test/setup.ts'],
    include: [
      'app/**/*.{spec,test}.ts',
      'test/**/*.{spec,test}.ts',
      'app/**/__tests__/*.{spec,test}.ts',
    ],
    exclude: ['node_modules', '.nuxt', '.output'],
  },
  resolve: {
    alias: [
      // pnpm 严格目录布局下 vite import-analysis 阶段找不到 @vueuse/core；
      // 用 stub 模块顶替，让 SFC 的 import 语句能解析。测试用例需要更细控制时
      // 可在文件内用 vi.mock 覆盖这个 stub。
      { find: '@vueuse/core', replacement: fileURLToPath(new URL('./test/vueuse-stub.ts', import.meta.url)) },
      // 更具体的 `~/test` / `@/test` 放在前面，确保优先匹配
      { find: '~/test', replacement: fileURLToPath(new URL('./test/', import.meta.url)) },
      { find: '@/test', replacement: fileURLToPath(new URL('./test/', import.meta.url)) },
      // 匹配 Nuxt 约定：`~` / `@` 指向 app/ 目录，与运行时一致
      { find: '~', replacement: fileURLToPath(new URL('./app/', import.meta.url)) },
      { find: '@', replacement: fileURLToPath(new URL('./app/', import.meta.url)) },
    ],
  },
})