package com.siteguard.system.service.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.siteguard.common.exception.AppException;
import com.siteguard.notify.enums.RobotPlatform;
import com.siteguard.system.config.CertAlertConfig;
import com.siteguard.system.config.NotificationConfig;
import com.siteguard.system.config.NotificationConfigMerger;
import com.siteguard.system.entity.SystemConfig;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.repository.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/// ConfigServiceImpl 单元测试（TDD 红 → 绿）。
///
/// 覆盖五个核心用例：
///   - getNode: 存在时反序列化；不存在时抛 AppException
///   - getOrDefault: 缺失时返回调用方传入的 fallback 实例本身（同一引用）
///   - set: 缺失则插入；存在则就地更新（不删除再插）
///   - delete: 缺失抛错；存在则调用 repository.delete
class ConfigServiceImplTest {

    private SystemConfigRepository repo;
    private ConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(SystemConfigRepository.class);
        var merger = new NotificationConfigMerger();
        service = new ConfigServiceImpl(repo, JsonMapper.builder().build(), merger);
    }

    @Test
    void get_existing_returnsParsedValue() {
        var entity = new SystemConfig();
        entity.setConfigKey("notification");
        entity.setConfigValue("{\"enabled\":true,\"platform\":\"DINGTALK\"}");
        when(repo.findByConfigKey("notification")).thenReturn(Optional.of(entity));

        JsonNode node = service.getNode(ConfigKey.NOTIFICATION);

        assertThat(node.get("enabled").asBoolean()).isTrue();
        assertThat(node.get("platform").asString()).isEqualTo("DINGTALK");
    }

    @Test
    void get_missing_throwsNotFound() {
        when(repo.findByConfigKey("notification")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNode(ConfigKey.NOTIFICATION))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("配置");
    }

    @Test
    void getOrDefault_missing_returnsFallback() {
        when(repo.findByConfigKey("notification")).thenReturn(Optional.empty());

        var fallback = new NotificationConfig();
        fallback.setEnabled(false);

        var result = service.getOrDefault(ConfigKey.NOTIFICATION, fallback);

        /// 必须返回同一引用，避免调用方修改 fallback 后产生幻觉
        assertThat(result).isSameAs(fallback);
        assertThat(result.getEnabled()).isFalse();
    }

    @Test
    void set_insertWhenAbsent() {
        when(repo.findByConfigKey("notification")).thenReturn(Optional.empty());
        when(repo.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        var cfg = new NotificationConfig();
        cfg.setEnabled(true);
        cfg.setPlatform(RobotPlatform.DINGTALK);
        cfg.setWebhookUrl("https://example.com/hook");

        service.set(ConfigKey.NOTIFICATION, cfg);

        var captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getConfigKey()).isEqualTo("notification");
        assertThat(captor.getValue().getConfigValue()).contains("\"enabled\":true");
    }

    @Test
    void set_updateWhenExists() {
        var existing = new SystemConfig();
        existing.setId(1L);
        existing.setConfigKey("notification");
        existing.setConfigValue("{\"enabled\":false}");
        when(repo.findByConfigKey("notification")).thenReturn(Optional.of(existing));
        when(repo.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        var cfg = new NotificationConfig();
        cfg.setEnabled(true);
        cfg.setPlatform(RobotPlatform.DINGTALK);
        cfg.setWebhookUrl("https://example.com/hook");

        service.set(ConfigKey.NOTIFICATION, cfg);

        /// 关键：不删再插，否则可能丢失 id 与审计字段
        verify(repo, never()).delete(existing);
        verify(repo).save(existing);
    }

    @Test
    void delete_missing_throwsNotFound() {
        when(repo.findByConfigKey("notification")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(ConfigKey.NOTIFICATION))
            .isInstanceOf(AppException.class);
    }

    @Test
    void delete_existing_removesEntity() {
        var existing = new SystemConfig();
        existing.setConfigKey("notification");
        when(repo.findByConfigKey("notification")).thenReturn(Optional.of(existing));

        service.delete(ConfigKey.NOTIFICATION);

        verify(repo).delete(existing);
    }

    /// CERT_ALERT 缺失：返回调用方传入的 fallback（含默认 [14,7,3]）
    /// 这是证书告警系统"零配置可用"的关键路径
    @Test
    void getOrDefault_certAlert_missing_returnsFallback() {
        when(repo.findByConfigKey("cert_alert")).thenReturn(Optional.empty());

        var fallback = CertAlertConfig.builder()
                .warningDays(CertAlertConfig.defaultWarningDays())
                .build();

        var result = service.getOrDefault(ConfigKey.CERT_ALERT, fallback);

        assertThat(result).isSameAs(fallback);
        assertThat(result.getWarningDaysOrDefault()).containsExactly(14, 7, 3);
    }

    /// OPEN_DASHBOARD 缺失：必须返回 false，绝不能默认 true。
    /// 这是"未配过 = 默认关闭"的不变量，关系到 /open/dashboard 是否会
    /// 「被隐式公开」——一旦测试挂掉，所有部署在首次启动后即可被未授权访问。
    @Test
    void getOrDefault_openDashboard_missing_returnsFalse() {
        when(repo.findByConfigKey("open_dashboard")).thenReturn(Optional.empty());

        var result = service.getOrDefault(ConfigKey.OPEN_DASHBOARD, false);

        assertThat(result).isFalse();
    }

    /// OPEN_DASHBOARD 已存为 true：读出 boolean 而不是 "true"/true 字符串。
    /// 反向回归：曾经有人把 Boolean 写成 String 写入，结果前端拿到字符串 truthy 表态。
    @Test
    void getOrDefault_openDashboard_true_returnsBoolean() {
        var existing = new SystemConfig();
        existing.setConfigKey("open_dashboard");
        existing.setConfigValue("true");
        when(repo.findByConfigKey("open_dashboard")).thenReturn(Optional.of(existing));

        var result = service.getOrDefault(ConfigKey.OPEN_DASHBOARD, false);

        assertThat(result).isEqualTo(Boolean.TRUE);
    }

    /// OPEN_DASHBOARD 已存为 false：读出 Boolean.FALSE，**不会** 因 boolean primitive
    /// 自动 unbox 而走到 NPE（autoboxing 1=null 边界）。
    @Test
    void getOrDefault_openDashboard_false_returnsBoolean() {
        var existing = new SystemConfig();
        existing.setConfigKey("open_dashboard");
        existing.setConfigValue("false");
        when(repo.findByConfigKey("open_dashboard")).thenReturn(Optional.of(existing));

        var result = service.getOrDefault(ConfigKey.OPEN_DASHBOARD, true);

        assertThat(result).isEqualTo(Boolean.FALSE);
    }

    /// set 路径：Boolean 写入必须正常序列化为 "true"/"false"。
    /// 验证 set 与 getOrDefault 之间序列化约定一致。
    @Test
    void set_openDashboard_true_storesJsonTrue() {
        when(repo.findByConfigKey("open_dashboard")).thenReturn(Optional.empty());
        when(repo.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        service.set(ConfigKey.OPEN_DASHBOARD, true);

        var captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getConfigKey()).isEqualTo("open_dashboard");
        assertThat(captor.getValue().getConfigValue()).isEqualTo("true");
    }
}