package com.siteguard.branding.service.impl;

import com.siteguard.branding.config.BrandingConfig;
import com.siteguard.branding.storage.BrandingIconStorage;
import com.siteguard.common.exception.AppException;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrandingServiceImplTest {

    private ConfigService configService;
    private BrandingIconStorage iconStorage;
    private BrandingServiceImpl service;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        iconStorage = mock(BrandingIconStorage.class);
        service = new BrandingServiceImpl(configService, iconStorage);
    }

    @Test
    void get_missingConfig_returnsCentralDefault() {
        when(configService.getOrDefault(eq(ConfigKey.BRANDING), any(BrandingConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        var response = service.get();

        assertThat(response.getName()).isEqualTo("Site Guard");
        assertThat(response.getIconUrl()).isEqualTo("/favicon.ico");
        assertThat(response.isCustomIcon()).isFalse();
    }

    @Test
    void set_trimsNameAndKeepsExistingIconWhenNoUpload() {
        var existing = BrandingConfig.builder().siteName("Old").iconVersion("a".repeat(64)).build();
        when(configService.getOrDefault(eq(ConfigKey.BRANDING), any(BrandingConfig.class))).thenReturn(existing);
        when(iconStorage.exists("a".repeat(64))).thenReturn(true);

        var response = service.set("  新站点  ", null);

        var captor = ArgumentCaptor.forClass(BrandingConfig.class);
        verify(configService).set(eq(ConfigKey.BRANDING), captor.capture());
        assertThat(captor.getValue().getSiteName()).isEqualTo("新站点");
        assertThat(captor.getValue().getIconVersion()).isEqualTo("a".repeat(64));
        assertThat(response.isCustomIcon()).isTrue();
    }

    @Test
    void set_uploadPersistsNewVersionThenDeletesOldFile() {
        var oldVersion = "a".repeat(64);
        var newVersion = "b".repeat(64);
        when(configService.getOrDefault(eq(ConfigKey.BRANDING), any(BrandingConfig.class)))
                .thenReturn(BrandingConfig.builder().siteName("Old").iconVersion(oldVersion).build());
        when(iconStorage.exists(oldVersion)).thenReturn(true);
        when(iconStorage.exists(newVersion)).thenReturn(true);
        var file = new MockMultipartFile("icon", "icon.png", "image/png", new byte[]{1});
        when(iconStorage.store(file)).thenReturn(newVersion);

        var response = service.set("New", file);

        var order = inOrder(iconStorage, configService);
        order.verify(iconStorage).store(file);
        order.verify(configService).set(eq(ConfigKey.BRANDING), any(BrandingConfig.class));
        order.verify(iconStorage).delete(oldVersion);
        assertThat(response.getIconUrl()).endsWith(newVersion);
    }

    @Test
    void set_kvFailureRollsBackNewFile() {
        var newVersion = "b".repeat(64);
        when(configService.getOrDefault(eq(ConfigKey.BRANDING), any(BrandingConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        var file = new MockMultipartFile("icon", "icon.png", "image/png", new byte[]{1});
        when(iconStorage.store(file)).thenReturn(newVersion);
        doThrow(new AppException("TEST", "write failed"))
                .when(configService).set(eq(ConfigKey.BRANDING), any(BrandingConfig.class));

        assertThatThrownBy(() -> service.set("New", file)).isInstanceOf(AppException.class);

        verify(iconStorage).delete(newVersion);
    }

    @Test
    void set_rejectsControlCharactersAndCountsUnicodeCodePoints() {
        assertThatThrownBy(() -> service.set("bad\nname", null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("控制字符");

        assertThatThrownBy(() -> service.set("😀".repeat(65), null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("64");
    }

    @Test
    void set_rejectsExplicitEmptyIconButAllowsMissingPart() {
        var empty = new MockMultipartFile("icon", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.set("Site", empty))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("不能为空");

        verify(iconStorage, never()).store(any());
        verify(configService, never()).set(eq(ConfigKey.BRANDING), any());
    }

    @Test
    void getIcon_onlyServesCurrentlyConfiguredVersion() {
        var configured = "a".repeat(64);
        when(configService.getOrDefault(eq(ConfigKey.BRANDING), any(BrandingConfig.class)))
                .thenReturn(BrandingConfig.builder().siteName("Site").iconVersion(configured).build());
        when(iconStorage.exists(configured)).thenReturn(true);
        when(iconStorage.read(configured)).thenReturn(new byte[]{1, 2});

        assertThat(service.getIcon(configured)).containsExactly(1, 2);
        assertThatThrownBy(() -> service.getIcon("b".repeat(64)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("不存在");
    }
}
