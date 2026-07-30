package com.siteguard.branding.service.impl;

import com.siteguard.branding.config.BrandingConfig;
import com.siteguard.branding.dto.BrandingResponse;
import com.siteguard.branding.service.BrandingService;
import com.siteguard.branding.storage.BrandingIconStorage;
import com.siteguard.common.exception.AppException;
import com.siteguard.common.exception.Errors;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/// 品牌配置服务实现。
@Service
@RequiredArgsConstructor
@Slf4j
public class BrandingServiceImpl implements BrandingService {

    public static final int MAX_SITE_NAME_CODE_POINTS = 64;
    public static final String DEFAULT_ICON_URL = "/favicon.ico";
    public static final String OPEN_ICON_PATH = "/api/v1/open/branding/icon/get?version=";

    private final ConfigService configService;
    private final BrandingIconStorage iconStorage;

    @Override
    public BrandingResponse get() {
        return toResponse(readEffectiveConfig());
    }

    @Override
    public synchronized BrandingResponse set(String siteName, MultipartFile icon) {
        var normalizedName = validateSiteName(siteName);
        var current = readEffectiveConfig();
        var previousVersion = validExistingVersion(current.getIconVersion());
        String newVersion = null;

        if (icon != null) {
            if (icon.isEmpty()) {
                throw Errors.INVALID_ARGUMENT.toException("品牌图标不能为空");
            }
            newVersion = iconStorage.store(icon);
        }

        var next = BrandingConfig.builder()
                .siteName(normalizedName)
                .iconVersion(newVersion != null ? newVersion : previousVersion)
                .build();
        try {
            configService.set(ConfigKey.BRANDING, next);
        } catch (RuntimeException e) {
            /// KV 写入失败时只删除本次新产生且未被旧配置引用的文件，避免留下孤儿。
            if (newVersion != null && !newVersion.equals(previousVersion)) {
                safelyDelete(newVersion);
            }
            throw e;
        }

        if (newVersion != null && previousVersion != null && !previousVersion.equals(newVersion)) {
            safelyDelete(previousVersion);
        }
        try {
            iconStorage.cleanupExcept(next.getIconVersion());
        } catch (RuntimeException e) {
            /// 配置已经成功切换，清理失败只会留下孤儿文件，不应把成功保存报告为失败。
            log.warn("清理品牌图标目录失败: {}", e.getMessage());
        }
        return toResponse(next);
    }

    @Override
    public synchronized BrandingResponse deleteIcon() {
        var current = readEffectiveConfig();
        var previousVersion = validExistingVersion(current.getIconVersion());
        var next = BrandingConfig.builder()
                .siteName(validateSiteName(current.getSiteName()))
                .iconVersion(null)
                .build();

        /// 先持久化“无图标”状态，再删文件：即使文件删除失败，公开接口也不会继续发布失效版本。
        configService.set(ConfigKey.BRANDING, next);
        if (previousVersion != null) {
            safelyDelete(previousVersion);
        }
        try {
            iconStorage.cleanupExcept(null);
        } catch (RuntimeException e) {
            /// KV 已经切换到默认图标，后续清理失败只会留下不可访问文件，不应把成功操作回滚成 500。
            log.warn("清理品牌图标目录失败: {}", e.getMessage());
        }
        return toResponse(next);
    }

    @Override
    public byte[] getIcon(String version) {
        var effective = readEffectiveConfig();
        var configuredVersion = validExistingVersion(effective.getIconVersion());
        if (configuredVersion == null || !configuredVersion.equals(version)) {
            throw Errors.NOT_FOUND.toException("品牌图标不存在");
        }
        return iconStorage.read(version);
    }

    private BrandingConfig readEffectiveConfig() {
        BrandingConfig stored;
        try {
            stored = configService.getOrDefault(ConfigKey.BRANDING, BrandingConfig.defaultValue());
        } catch (AppException e) {
            /// 品牌展示不能因历史脏数据拖垮登录页；异常配置统一回退并记录告警。
            log.warn("读取品牌配置失败，使用默认品牌: {}", e.getMessage());
            return BrandingConfig.defaultValue();
        }
        if (stored == null) {
            return BrandingConfig.defaultValue();
        }

        String siteName;
        try {
            siteName = validateSiteName(stored.getSiteName());
        } catch (AppException e) {
            log.warn("品牌名称无效，使用默认名称: {}", e.getMessage());
            siteName = BrandingConfig.DEFAULT_SITE_NAME;
        }
        return BrandingConfig.builder()
                .siteName(siteName)
                .iconVersion(validExistingVersion(stored.getIconVersion()))
                .build();
    }

    private String validExistingVersion(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        try {
            return iconStorage.exists(version) ? version : null;
        } catch (AppException e) {
            log.warn("忽略无效的品牌图标版本: {}", e.getMessage());
            return null;
        }
    }

    private String validateSiteName(String siteName) {
        if (siteName == null) {
            throw Errors.INVALID_ARGUMENT.toException("站点名称不能为空");
        }
        var normalized = siteName.trim();
        if (normalized.isEmpty()) {
            throw Errors.INVALID_ARGUMENT.toException("站点名称不能为空");
        }
        if (normalized.codePointCount(0, normalized.length()) > MAX_SITE_NAME_CODE_POINTS) {
            throw Errors.INVALID_ARGUMENT.toException("站点名称不能超过 64 个字符");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw Errors.INVALID_ARGUMENT.toException("站点名称不能包含控制字符");
        }
        return normalized;
    }

    private BrandingResponse toResponse(BrandingConfig config) {
        var version = validExistingVersion(config.getIconVersion());
        return BrandingResponse.builder()
                .name(config.getSiteName())
                .iconUrl(version == null ? DEFAULT_ICON_URL : OPEN_ICON_PATH + version)
                .customIcon(version != null)
                .build();
    }

    private void safelyDelete(String version) {
        try {
            iconStorage.delete(version);
        } catch (RuntimeException e) {
            log.warn("清理品牌图标失败，version={}: {}", version, e.getMessage());
        }
    }
}
