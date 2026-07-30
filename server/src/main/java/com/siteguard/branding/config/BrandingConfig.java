package com.siteguard.branding.config;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// 站点品牌持久化配置，仅保存名称和当前图标版本。
///
/// 图标 URL、默认图标和是否为自定义图标均由服务层集中推导，避免 KV 中保存可失效的派生字段。
@Data
@NoArgsConstructor
public class BrandingConfig {

    public static final String DEFAULT_SITE_NAME = "Site Guard";

    private String siteName;

    private String iconVersion;

    @Builder
    public BrandingConfig(String siteName, String iconVersion) {
        this.siteName = siteName;
        this.iconVersion = iconVersion;
    }

    /// 集中默认值；每次返回新对象，防止调用方修改共享实例。
    public static BrandingConfig defaultValue() {
        return BrandingConfig.builder()
                .siteName(DEFAULT_SITE_NAME)
                .iconVersion(null)
                .build();
    }
}
