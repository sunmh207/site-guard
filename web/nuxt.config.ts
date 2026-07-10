// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
    compatibilityDate: '2025-07-15',
    ssr: false,
    devtools: {enabled: true},
    // CSS 配置 - 确保样式正确加载
    css: ['~/assets/css/main.css'],
    icon: {
        provider: 'none',
        clientBundle: {
            icons: [
                'lucide:chevron-down',
                'lucide:chevron-up',
                'lucide:menu',
                'lucide:panel-left-close',
                'lucide:panel-left-open',
                'lucide:chevrons-left',
                'lucide:chevron-left',
                'lucide:chevrons-right',
                'lucide:loader-circle',
                'lucide:x',
                'lucide:folder-open',
                'lucide:folder',
                'lucide:check-check',
                'lucide:square-square',
                'lucide:message-circle-question',
                'lucide:folder-closed',
                'lucide:chevrons-up-down',
                'lucide:sun-moon',
                'lucide:sun',
                'lucide:moon',
                'lucide:log-out',
                'lucide:code',
                'lucide:layout-dashboard',
                'lucide:folder-kanban',
                'lucide:settings',
                'lucide:circle-alert',
                'lucide:circle-check',
            ],
            // scan all components in the project and include icons
            scan: true,
            // include all custom collections in the client bundle
            includeCustomCollections: true,
            // guard for uncompressed bundle size, will fail the build if exceeds
            sizeLimitKb: 256,
        },
    },
    // 模块配置
    modules: [
        '@nuxt/eslint',
        '@nuxt/scripts',
        '@nuxt/test-utils',
        '@nuxt/ui',
        '@pinia/nuxt',
        '@vueuse/nuxt',
    ],

    // 自动导入配置
    imports: {
        dirs: [
            'features/*/composables',
            'features/*/types',
            'shared/composables',
            'shared/types',
        ],
    },

    // 组件自动导入
    components: [
        { path: '~/features', pathPrefix: false, extensions: ['.vue'] },
        { path: '~/shared/components', pathPrefix: false },
    ],
    ui: {
        fonts: false,
        colorMode: true,
    },

    // 开发服务器配置
    devServer: {
        port: 3001,
    },

    // 运行时配置
    runtimeConfig: {
        // 公共配置（客户端和服务端都可用）
        public: {
            apiBaseUrl: '/api/v1',
        },
    },

    // Nitro 配置 - 代理后端 API
    nitro: {
        routeRules: {
            '/api/v1/**':
                {
                    proxy:
                        {
                            to: 'http://127.0.0.1:8080/api/v1/**',
                        },
                },
        },
    },

    // 颜色主题配置 - 绿色主色调
    colorMode: {
        preference: 'system',
        fallback: 'light',
    },
})
