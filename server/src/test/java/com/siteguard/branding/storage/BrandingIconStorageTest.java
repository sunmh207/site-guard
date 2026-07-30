package com.siteguard.branding.storage;

import com.siteguard.branding.config.BrandingStorageProperties;
import com.siteguard.common.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrandingIconStorageTest {

    @TempDir
    Path tempDir;

    private BrandingIconStorage storage;

    @BeforeEach
    void setUp() {
        var properties = new BrandingStorageProperties();
        properties.setDirectory(tempDir.toString());
        storage = new BrandingIconStorage(properties);
    }

    @Test
    void store_reencodesAndScalesImageWithSha256Version() throws Exception {
        var source = image("jpeg", 1024, 256);
        var version = storage.store(new MockMultipartFile("icon", "fake.png", "image/png", source));

        assertThat(version).matches("[0-9a-f]{64}");
        assertThat(storage.exists(version)).isTrue();
        var decoded = ImageIO.read(storage.path(version).toFile());
        assertThat(decoded.getWidth()).isEqualTo(512);
        assertThat(decoded.getHeight()).isEqualTo(128);
        assertThat(Files.readAllBytes(storage.path(version))).startsWith(
                (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
    }

    @Test
    void store_rejectsSpoofedAndOversizedUploads() {
        assertThatThrownBy(() -> storage.store(
                new MockMultipartFile("icon", "x.png", "image/png", "not image".getBytes())))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("PNG/JPEG");

        var oversized = new byte[2 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> storage.store(
                new MockMultipartFile("icon", "x.jpg", "image/jpeg", oversized)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("2 MiB");
    }

    @Test
    void versionValidationPreventsPathTraversalAndCleanupRetainsSelectedVersion() throws Exception {
        var retained = storage.store(new MockMultipartFile("icon", "a.png", "image/png", image("png", 32, 32)));
        var removed = storage.store(new MockMultipartFile("icon", "b.png", "image/png", image("png", 48, 32)));

        assertThatThrownBy(() -> storage.read("../../etc/passwd"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("版本不合法");

        storage.cleanupExcept(retained);

        assertThat(storage.exists(retained)).isTrue();
        assertThat(storage.exists(removed)).isFalse();
    }

    private byte[] image(String format, int width, int height) throws Exception {
        var image = new BufferedImage(width, height,
                "jpeg".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, output);
            return output.toByteArray();
        }
    }
}
