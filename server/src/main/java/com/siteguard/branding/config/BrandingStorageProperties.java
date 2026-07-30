package com.siteguard.branding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/// 品牌图标文件存储配置。
@Component
@ConfigurationProperties(prefix = "app.branding.storage")
@Data
public class BrandingStorageProperties {

    /// 默认与 Docker 持久化数据卷保持一致。
    private String directory = "./data/branding";

    /// 原始上传体最大 2 MiB。
    private long maxUploadBytes = 2L * 1024 * 1024;

    /// 解码前限制边长及总像素，阻止压缩炸弹耗尽堆内存。
    private int maxInputDimension = 8192;
    private long maxInputPixels = 16L * 1024 * 1024;

    /// 对外输出统一缩放至 512 像素以内并重新编码 PNG。
    private int maxOutputDimension = 512;
    private long maxOutputBytes = 2L * 1024 * 1024;
}
