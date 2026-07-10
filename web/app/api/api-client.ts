import { getAccessToken, removeAccessToken } from "~/shared/utils/tokenStorage"
import { refreshAccessToken } from "~/shared/utils/tokenRefresh"
import { ROUTES } from "~/shared/constants/routes"
import type { ApiErrorResponse } from "~/shared/types/api"

/**
 * 处理 401 未授权响应
 * 清除本地令牌、用户状态并跳转到登录页
 */
function handleUnauthorized() {
    if (import.meta.client) {
        removeAccessToken()
        useState('auth:user').value = null
        navigateTo(ROUTES.LOGIN)
    }
}

/**
 * 显示错误提示
 * 根据 HTTP 状态码选择合适的提示类型
 */
function displayErrorToast(error: any) {
    if (!import.meta.client) return

    const message = useMessage()
    const status = error?.response?.status || error?.statusCode || 500

    // 401/403 不显示错误提示（由 token 刷新逻辑处理）
    // 404 不显示：通常是「资源不存在」，交由调用方自行处理
    // （如设置页把 404 当「未配置」态，在卡片内提示，而非弹 toast 骚扰用户）
    if (status === 401 || status === 403 || status === 404) {
        return
    }

    // 提取错误信息
    const errorData: ApiErrorResponse | undefined = error?.response?._data || error?.data
    const errorMessage = errorData?.message || error?.message || '请求失败，请稍后重试'

    // 根据状态码选择提示类型
    if (status >= 500) {
        message.error(errorMessage)
    } else if (status >= 400) {
        message.warning(errorMessage)
    } else {
        message.error(errorMessage)
    }
}

/**
 * 带认证的 useFetch 包装器
 * 自动添加认证头，401/403 时尝试刷新 token
 */
export function useApi<T = any>(url: string | (() => string) | null, options?: Parameters<typeof useFetch<T>>[1]) {
    // 使用 computed 确保在客户端获取最新的 token
    const token = computed(() => getAccessToken())

    // 如果 url 是函数，需要检查其返回值是否为 null
    const shouldSkip = computed(() => {
        if (typeof url === 'function') {
            return url() === null
        }
        return url === null
    })

    return useFetch<T>(url, {
        cache: 'no-store',
        ...options,
        server:false, //列表页数据不在 SSR 阶段请求，交给客户端处理。在401/403时，客户端会自动刷新token，再次请求，避免页面出现4xx错误。
        // 禁用 Nuxt 默认的错误 toast 提示
        dedupe: 'defer',
        // 当 URL 为 null 时跳过请求
        skip: computed(() => shouldSkip.value || (options?.skip as any)?.()),
        headers: computed(() => {
            const headers: Record<string, string> = {
                ...(options?.headers as Record<string, string> || {}),
            }
            if (token.value) {
                headers.Authorization = `Bearer ${token.value}`
            }
            return headers
        }),
        async onResponseError({ response }) {
            // 处理 401 (未认证) 或 403 (token 过期导致的无权限)
            if (response.status === 401 || response.status === 403) {
                console.log(`[API] 收到 ${response.status} 响应，尝试刷新 token...`)

                try {
                    // 尝试刷新 token
                    await refreshAccessToken()

                    // 刷新成功，但 useFetch 不支持自动重试
                    // 用户需要手动重新触发请求
                    console.log('[API] Token 刷新成功，请重新发起请求')
                } catch (error) {
                    // 刷新失败，已在 tokenRefresh 中处理跳转
                    console.error('[API] Token 刷新失败')
                }
            } else {
                // 其他错误显示提示
                displayErrorToast({ response })
            }
        },
    })
}

/**
 * 带认证的 $fetch 包装器
 * 自动添加认证头，401/403 时自动刷新 token 并重试
 */
export async function $api<T>(url: string, options?: Parameters<typeof $fetch>[1]): Promise<T> {
    const token = getAccessToken()

    try {
        return await $fetch<T>(url, {
            ...options,
            headers: {
                ...options?.headers,
                ...(token ? { Authorization: `Bearer ${token}` } : {}),
            },
        })
    } catch (error: any) {
        // 检查是否是 401 (未认证) 或 403 (token 过期导致的无权限) 错误
        const status = error?.response?.status || error?.statusCode
        if (status === 401 || status === 403) {
            console.log(`[API] 收到 ${status} 响应，尝试刷新 token...`)

            try {
                // 尝试刷新 token
                const newToken = await refreshAccessToken()

                // 使用新 token 重试请求
                console.log('[API] Token 刷新成功，重试请求...')
                return await $fetch<T>(url, {
                    ...options,
                    headers: {
                        ...options?.headers,
                        Authorization: `Bearer ${newToken}`,
                    },
                })
            } catch (refreshError) {
                // 刷新失败，已在 tokenRefresh 中处理跳转
                console.error('[API] Token 刷新失败，终止请求')
                throw refreshError
            }
        }

        // 其他错误显示提示并抛出
        displayErrorToast(error)
        throw error
    }
}
