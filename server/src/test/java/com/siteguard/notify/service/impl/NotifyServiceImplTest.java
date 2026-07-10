package com.siteguard.notify.service.impl;

import com.siteguard.common.exception.AppException;
import com.siteguard.notify.entity.ImRobot;
import com.siteguard.notify.enums.RobotPlatform;
import com.siteguard.notify.sender.ImSender;
import com.siteguard.system.config.NotificationConfig;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NotifyServiceImplTest {

    private ConfigService configService;
    private EnumMap<RobotPlatform, ImSender> senderMap;
    private NotifyServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        configService = mock(ConfigService.class);
        senderMap = mock(EnumMap.class);
        service = new NotifyServiceImpl(configService, senderMap);
    }

    @Test
    void send_notConfigured_throws() {
        when(configService.get(ConfigKey.NOTIFICATION)).thenReturn(null);

        assertThatThrownBy(() -> service.send("title", "hello"))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("尚未配置");
    }

    @Test
    void send_disabled_throws() {
        var cfg = new NotificationConfig();
        cfg.setEnabled(false);
        cfg.setPlatform(RobotPlatform.DINGTALK);
        when(configService.get(ConfigKey.NOTIFICATION)).thenReturn(cfg);

        assertThatThrownBy(() -> service.send("title", "hello"))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("未启用");
    }

    @Test
    void send_dispatchesToSender() {
        var cfg = NotificationConfig.builder()
            .enabled(true)
            .platform(RobotPlatform.DINGTALK)
            .webhookUrl("https://example.com")
            .secret(null)
            .build();
        when(configService.get(ConfigKey.NOTIFICATION)).thenReturn(cfg);
        ImSender sender = mock(ImSender.class);
        when(senderMap.get(RobotPlatform.DINGTALK)).thenReturn(sender);

        service.send("告警标题", "hello world");

        var captor = ArgumentCaptor.forClass(ImRobot.class);
        verify(sender).send(captor.capture(), eq("告警标题"), eq("hello world"));
        assertThat(captor.getValue().getPlatform()).isEqualTo(RobotPlatform.DINGTALK);
        assertThat(captor.getValue().getWebhookUrl()).isEqualTo("https://example.com");
    }

    @Test
    void send_unknownPlatform_throws() {
        var cfg = NotificationConfig.builder()
            .enabled(true)
            .platform(null)
            .webhookUrl("https://x")
            .build();
        when(configService.get(ConfigKey.NOTIFICATION)).thenReturn(cfg);
        when(senderMap.get(null)).thenReturn(null);

        assertThatThrownBy(() -> service.send("title", "hello"))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("不支持的平台");
    }
}