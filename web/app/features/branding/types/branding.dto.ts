/// 站点品牌配置 DTO。
///
/// 公开与管理接口都返回同一结构；customIcon 用于区分后端自定义图片与
/// 默认 favicon，前端据此决定渲染图片还是内置雷达图标。
export interface BrandingDto {
  name: string
  iconUrl: string
  customIcon: boolean
}

/// 所有公开页面的稳定回退值。对象冻结，避免调用方意外修改全局默认配置。
export const DEFAULT_BRANDING: Readonly<BrandingDto> = Object.freeze({
  name: 'Site Guard',
  iconUrl: '/favicon.ico',
  customIcon: false,
})
