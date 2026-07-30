package com.siteguard.api.open;

import com.siteguard.branding.dto.BrandingResponse;
import com.siteguard.branding.service.BrandingService;
import com.siteguard.branding.storage.BrandingIconStorage;
import com.siteguard.common.dto.StatusResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/// 无需认证的站点品牌读取接口。
@RestController
@RequestMapping("/api/v1/open/branding")
@RequiredArgsConstructor
@Tag(name = "公开站点品牌", description = "登录页和公开页面使用的站点品牌")
public class OpenBrandingController {

    private final BrandingService brandingService;

    @GetMapping("/get")
    @Operation(summary = "读取公开品牌配置")
    public StatusResult<BrandingResponse> get() {
        return StatusResult.success(brandingService.get());
    }

    @GetMapping("/icon/get")
    @Operation(summary = "读取版本化品牌图标")
    public ResponseEntity<byte[]> getIcon(
            @RequestParam String version,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        var etag = '"' + version + '"';
        var content = brandingService.getIcon(version);
        var headers = iconHeaders(etag);
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).headers(headers).build();
        }
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(content.length)
                .body(content);
    }

    /// 内容地址包含 SHA-256 版本，可安全使用长期 immutable 缓存。
    private HttpHeaders iconHeaders(String etag) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(BrandingIconStorage.PNG_MEDIA_TYPE));
        headers.setETag(etag);
        headers.setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
        headers.set("X-Content-Type-Options", "nosniff");
        return headers;
    }
}
