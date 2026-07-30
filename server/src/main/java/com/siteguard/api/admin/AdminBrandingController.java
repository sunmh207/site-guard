package com.siteguard.api.admin;

import com.siteguard.branding.dto.BrandingResponse;
import com.siteguard.branding.service.BrandingService;
import com.siteguard.common.dto.StatusResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/// 管理端站点品牌接口；品牌 KV 只能经该领域接口修改。
@RestController
@RequestMapping("/api/v1/admin/branding")
@RequiredArgsConstructor
@Tag(name = "站点品牌", description = "管理站点名称与图标")
public class AdminBrandingController {

    private final BrandingService brandingService;

    @GetMapping("/get")
    @Operation(summary = "读取品牌配置")
    public StatusResult<BrandingResponse> get() {
        return StatusResult.success(brandingService.get());
    }

    @PostMapping(value = "/set", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "保存品牌名称及可选图标")
    public StatusResult<BrandingResponse> set(
            @RequestParam("siteName") String siteName,
            @RequestPart(value = "icon", required = false) MultipartFile icon) {
        return StatusResult.success(brandingService.set(siteName, icon));
    }

    @PostMapping("/icon/delete")
    @Operation(summary = "删除自定义品牌图标")
    public StatusResult<BrandingResponse> deleteIcon() {
        return StatusResult.success(brandingService.deleteIcon());
    }
}
