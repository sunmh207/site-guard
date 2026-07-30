/// 站点品牌配置 API。
///
/// 图片上传直接把 FormData 交给 $fetch；浏览器会自动附带 multipart boundary，
/// 不能手动设置 Content-Type，否则 boundary 会丢失。
import { $adminApi } from '~/api/admin-api-client'
import { $openApi } from '~/api/open-api-client'
import type { StatusResult } from '~/shared/types/api'
import type { BrandingDto } from '../types/branding.dto'

export const brandingApi = {
  /// 无需认证的品牌配置，供登录页、公开大屏和应用启动阶段读取。
  getPublic(): Promise<StatusResult<BrandingDto>> {
    return $openApi<StatusResult<BrandingDto>>('/branding/get')
  },

  /// 管理端品牌配置，供设置卡片读取后端权威值。
  getAdmin(): Promise<StatusResult<BrandingDto>> {
    return $adminApi<StatusResult<BrandingDto>>('/branding/get')
  },

  /// 保存名称及可选图片。
  set(formData: FormData): Promise<StatusResult<BrandingDto>> {
    return $adminApi<StatusResult<BrandingDto>>('/branding/set', {
      method: 'POST',
      body: formData,
    })
  },

  /// 删除自定义图片并回退到默认图标。
  deleteIcon(): Promise<StatusResult<BrandingDto>> {
    return $adminApi<StatusResult<BrandingDto>>('/branding/icon/delete', {
      method: 'POST',
    })
  },
}
