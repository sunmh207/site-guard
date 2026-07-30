package com.siteguard.branding.storage;

import com.siteguard.branding.config.BrandingStorageProperties;
import com.siteguard.common.exception.Errors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/// 品牌图标的受限文件存储。
///
/// 只接受实际可解码的 PNG/JPEG；解码前先用 ImageIO reader 读取尺寸，输出统一为 PNG，
/// 并使用内容 SHA-256 作为不可变版本及文件名。
@Component
@RequiredArgsConstructor
public class BrandingIconStorage {

    public static final String PNG_MEDIA_TYPE = "image/png";
    private static final String FILE_SUFFIX = ".png";
    private static final int SHA256_HEX_LENGTH = 64;
    private static final Set<String> ALLOWED_FORMATS = Set.of("png", "jpeg", "jpg");

    private final BrandingStorageProperties properties;

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw Errors.INVALID_ARGUMENT.toException("品牌图标不能为空");
        }
        if (file.getSize() > properties.getMaxUploadBytes()) {
            throw Errors.INVALID_ARGUMENT.toException("品牌图标不能超过 2 MiB");
        }

        byte[] source;
        try {
            source = file.getBytes();
        } catch (IOException e) {
            throw Errors.INTERNAL_ERROR.toException(e, "读取品牌图标失败");
        }
        if (source.length == 0 || source.length > properties.getMaxUploadBytes()) {
            throw Errors.INVALID_ARGUMENT.toException("品牌图标不能为空且不能超过 2 MiB");
        }

        var normalized = normalizeImage(source);
        if (normalized.length > properties.getMaxOutputBytes()) {
            throw Errors.INVALID_ARGUMENT.toException("处理后的品牌图标超过输出限制");
        }

        var version = sha256(normalized);
        var target = resolveVersion(version);
        try {
            Files.createDirectories(storageDirectory());
            if (!Files.exists(target)) {
                /// 临时文件必须和目标同目录，才能在支持的文件系统上使用原子 move。
                var temporary = Files.createTempFile(storageDirectory(), ".branding-", ".tmp");
                try {
                    Files.write(temporary, normalized);
                    moveAtomically(temporary, target);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            return version;
        } catch (IOException e) {
            throw Errors.INTERNAL_ERROR.toException(e, "保存品牌图标失败");
        }
    }

    public byte[] read(String version) {
        var path = resolveVersion(version);
        if (!Files.isRegularFile(path)) {
            throw Errors.NOT_FOUND.toException("品牌图标不存在");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw Errors.INTERNAL_ERROR.toException(e, "读取品牌图标失败");
        }
    }

    public boolean exists(String version) {
        return Files.isRegularFile(resolveVersion(version));
    }

    public void delete(String version) {
        var path = resolveVersion(version);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw Errors.INTERNAL_ERROR.toException(e, "删除品牌图标失败");
        }
    }

    /// 清理目录内除保留版本外的所有规范品牌图标；临时文件也会一并回收。
    public void cleanupExcept(String retainedVersion) {
        if (retainedVersion != null) {
            validateVersion(retainedVersion);
        }
        var directory = storageDirectory();
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> isManagedFile(path, retainedVersion))
                    .forEach(this::deletePath);
        } catch (IOException e) {
            throw Errors.INTERNAL_ERROR.toException(e, "清理品牌图标失败");
        }
    }

    public Path path(String version) {
        return resolveVersion(version);
    }

    private byte[] normalizeImage(byte[] source) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (input == null) {
                throw Errors.INVALID_ARGUMENT.toException("品牌图标不是有效的 PNG/JPEG 图片");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw Errors.INVALID_ARGUMENT.toException("品牌图标不是有效的 PNG/JPEG 图片");
            }
            var reader = readers.next();
            try {
                var format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!ALLOWED_FORMATS.contains(format)) {
                    throw Errors.INVALID_ARGUMENT.toException("品牌图标仅支持 PNG/JPEG");
                }
                reader.setInput(input, true, true);
                var width = reader.getWidth(0);
                var height = reader.getHeight(0);
                validateDimensions(width, height);
                var decoded = reader.read(0);
                if (decoded == null) {
                    throw Errors.INVALID_ARGUMENT.toException("品牌图标解码失败");
                }
                return encodePng(scale(decoded));
            } finally {
                reader.dispose();
            }
        } catch (com.siteguard.common.exception.AppException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw Errors.INVALID_ARGUMENT.toException(e, "品牌图标不是有效的 PNG/JPEG 图片");
        }
    }

    private void validateDimensions(int width, int height) {
        var pixels = (long) width * height;
        if (width <= 0 || height <= 0
                || width > properties.getMaxInputDimension()
                || height > properties.getMaxInputDimension()
                || pixels > properties.getMaxInputPixels()) {
            throw Errors.INVALID_ARGUMENT.toException("品牌图标尺寸过大");
        }
    }

    private BufferedImage scale(BufferedImage source) {
        var limit = properties.getMaxOutputDimension();
        var scale = Math.min(1.0d, Math.min((double) limit / source.getWidth(), (double) limit / source.getHeight()));
        var width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        var height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        var target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] encodePng(BufferedImage image) throws IOException {
        try (var output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw Errors.INTERNAL_ERROR.toException("系统不支持 PNG 编码");
            }
            return output.toByteArray();
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw Errors.INTERNAL_ERROR.toException(e, "系统不支持 SHA-256");
        }
    }

    private Path resolveVersion(String version) {
        validateVersion(version);
        var directory = storageDirectory();
        var resolved = directory.resolve(version + FILE_SUFFIX).normalize();
        if (!resolved.getParent().equals(directory)) {
            throw Errors.INVALID_ARGUMENT.toException("品牌图标版本不合法");
        }
        return resolved;
    }

    private void validateVersion(String version) {
        if (version == null || version.length() != SHA256_HEX_LENGTH) {
            throw Errors.INVALID_ARGUMENT.toException("品牌图标版本不合法");
        }
        for (int i = 0; i < version.length(); i++) {
            char ch = version.charAt(i);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'))) {
                throw Errors.INVALID_ARGUMENT.toException("品牌图标版本不合法");
            }
        }
    }

    private Path storageDirectory() {
        return Path.of(properties.getDirectory()).toAbsolutePath().normalize();
    }

    private boolean isManagedFile(Path path, String retainedVersion) {
        var name = path.getFileName().toString();
        if (name.startsWith(".branding-") && name.endsWith(".tmp")) {
            return true;
        }
        if (!name.endsWith(FILE_SUFFIX)) {
            return false;
        }
        var version = name.substring(0, name.length() - FILE_SUFFIX.length());
        if (retainedVersion != null && retainedVersion.equals(version)) {
            return false;
        }
        try {
            validateVersion(version);
            return true;
        } catch (com.siteguard.common.exception.AppException ignored) {
            return false;
        }
    }

    private void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw Errors.INTERNAL_ERROR.toException(e, "清理品牌图标失败");
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        /// 不降级为普通 move：不支持原子重命名时宁可失败，也不能暴露半写入文件。
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }
}
