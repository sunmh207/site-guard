package com.siteguard.branding.dto;

import lombok.Builder;
import lombok.Data;

/// 前端品牌展示所需的稳定响应结构。
@Data
@Builder
public class BrandingResponse {

    private String name;

    private String iconUrl;

    private boolean customIcon;
}
