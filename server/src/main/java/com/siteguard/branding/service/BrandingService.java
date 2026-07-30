package com.siteguard.branding.service;

import com.siteguard.branding.dto.BrandingResponse;
import org.springframework.web.multipart.MultipartFile;

/// 品牌配置领域服务，负责协调 KV 配置与图标文件的一致性。
public interface BrandingService {

    BrandingResponse get();

    BrandingResponse set(String siteName, MultipartFile icon);

    BrandingResponse deleteIcon();

    byte[] getIcon(String version);
}
