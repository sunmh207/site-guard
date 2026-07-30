import { brandingApi } from '../api/branding.api'
import { DEFAULT_BRANDING, type BrandingDto } from '../types/branding.dto'

/// 当前应用实例中的公开请求。模块变量负责跨组件并发去重；状态本身仍由 useState
/// 承载，因此页面切换和多个 composable 调用方共享同一份响应式数据。
let pendingLoad: Promise<void> | null = null
let requestGeneration = 0

function normalizeBranding(value?: Partial<BrandingDto> | null): BrandingDto {
  return {
    name: value?.name?.trim() || DEFAULT_BRANDING.name,
    iconUrl: value?.iconUrl || DEFAULT_BRANDING.iconUrl,
    customIcon: value?.customIcon === true,
  }
}

export function useBranding() {
  const data = useState<BrandingDto>('branding:data', () => ({ ...DEFAULT_BRANDING }))
  const loaded = useState<boolean>('branding:loaded', () => false)
  const loading = useState<boolean>('branding:loading', () => false)

  /// 强制重新读取公开配置。generation 快照可防止较早发出的请求在设置保存并
  /// apply 后才返回，从而用旧值覆盖刚保存的品牌状态。
  function refresh(): Promise<void> {
    if (pendingLoad) return pendingLoad

    const generation = requestGeneration
    loading.value = true
    pendingLoad = (async () => {
      try {
        const res = await brandingApi.getPublic()
        if (generation === requestGeneration) {
          data.value = normalizeBranding(res.data)
          loaded.value = true
        }
      }
      catch {
        /// 品牌信息不应阻断应用启动；请求失败时保留当前值（初始即 DEFAULT_BRANDING）。
      }
      finally {
        if (generation === requestGeneration) {
          loading.value = false
        }
        pendingLoad = null
      }
    })()

    return pendingLoad
  }

  function ensureLoaded(): Promise<void> {
    if (loaded.value) return Promise.resolve()
    return refresh()
  }

  function apply(next: BrandingDto) {
    /// 令所有已发出的 load 失效，并立即把管理端保存响应同步到整个应用。
    requestGeneration += 1
    data.value = normalizeBranding(next)
    loaded.value = true
    loading.value = false
  }

  return {
    data,
    loaded,
    loading,
    ensureLoaded,
    refresh,
    apply,
  }
}
