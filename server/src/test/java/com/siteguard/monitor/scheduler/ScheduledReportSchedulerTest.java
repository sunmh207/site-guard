package com.siteguard.monitor.scheduler;

import com.siteguard.system.config.ScheduledReportConfig;
import com.siteguard.system.entity.SystemConfig;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.repository.SystemConfigRepository;
import com.siteguard.system.service.ConfigService;
import com.siteguard.monitor.service.ScheduledReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// 验证调度器 tick 的三路判定：enabled / 时刻匹配 / 今日已发。
///
/// 时刻匹配不直接控制"当前时间"，而是靠 cfg.time 让 LocalTime.now() 落在 ±1 分钟容差内：
///   - matchesTime_true   → 设 time 为当前时刻（必匹配）
///   - matchesTime_false  → 设 time 为 00:00（除非半夜测试，否则必不匹配）
/// 这避免了对 java.time.Clock 注入的侵入，同时仍覆盖判定分支。
@ExtendWith(MockitoExtension.class)
class ScheduledReportSchedulerTest {

    @Mock
    ConfigService configService;

    @Mock
    SystemConfigRepository systemConfigRepository;

    @Mock
    ScheduledReportService scheduledReportService;

    @InjectMocks
    ScheduledReportScheduler scheduler;

    private static ScheduledReportConfig cfgEnabled(String time) {
        ScheduledReportConfig cfg = new ScheduledReportConfig();
        cfg.setEnabled(true);
        cfg.setTime(time);
        return cfg;
    }

    private static ScheduledReportConfig cfgDisabled() {
        ScheduledReportConfig cfg = new ScheduledReportConfig();
        cfg.setEnabled(false);
        cfg.setTime("08:00");
        return cfg;
    }

    /// 当前时刻格式化为 HH:mm，保证 matchesTime 落在容差内。
    private static String now() {
        return LocalDate.now().atTime(java.time.LocalTime.now()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    @Test
    void tick_disabled_doesNotCallService() {
        when(configService.getOrDefault(eq(ConfigKey.SCHEDULED_REPORT), any(ScheduledReportConfig.class)))
                .thenReturn(cfgDisabled());

        scheduler.tick();

        verify(scheduledReportService, never()).generateAndSend();
    }

    @Test
    void tick_timeNotMatched_doesNotCallService() {
        when(configService.getOrDefault(eq(ConfigKey.SCHEDULED_REPORT), any(ScheduledReportConfig.class)))
                .thenReturn(cfgEnabled("00:00"));

        scheduler.tick();

        verify(scheduledReportService, never()).generateAndSend();
    }

    @Test
    void tick_timeMatchedAndNotSentToday_callsServiceOnce() {
        when(configService.getOrDefault(eq(ConfigKey.SCHEDULED_REPORT), any(ScheduledReportConfig.class)))
                .thenReturn(cfgEnabled(now()));
        when(systemConfigRepository.findByConfigKey("scheduled_report_last_sent"))
                .thenReturn(Optional.empty());

        scheduler.tick();

        verify(scheduledReportService).generateAndSend();
    }

    @Test
    void tick_alreadySentToday_doesNotCallService() {
        when(configService.getOrDefault(eq(ConfigKey.SCHEDULED_REPORT), any(ScheduledReportConfig.class)))
                .thenReturn(cfgEnabled(now()));
        SystemConfig entity = new SystemConfig();
        entity.setConfigValue(LocalDate.now().toString());
        when(systemConfigRepository.findByConfigKey("scheduled_report_last_sent"))
                .thenReturn(Optional.of(entity));

        scheduler.tick();

        verify(scheduledReportService, never()).generateAndSend();
    }

    @Test
    void tick_serviceThrows_doesNotMarkSentToday() {
        when(configService.getOrDefault(eq(ConfigKey.SCHEDULED_REPORT), any(ScheduledReportConfig.class)))
                .thenReturn(cfgEnabled(now()));
        when(systemConfigRepository.findByConfigKey("scheduled_report_last_sent"))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("send failed")).when(scheduledReportService).generateAndSend();

        scheduler.tick();

        /// 关键不变量：发送失败 ≡ 未发送，lastSentDate 不能更新。
        verify(systemConfigRepository, never()).save(any(SystemConfig.class));
    }

    @Test
    void tick_serviceSucceeds_marksSentToday() {
        when(configService.getOrDefault(eq(ConfigKey.SCHEDULED_REPORT), any(ScheduledReportConfig.class)))
                .thenReturn(cfgEnabled(now()));
        when(systemConfigRepository.findByConfigKey("scheduled_report_last_sent"))
                .thenReturn(Optional.empty());

        scheduler.tick();

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigRepository).save(captor.capture());
        assertEquals(LocalDate.now().toString(), captor.getValue().getConfigValue());
    }

    @Test
    void tick_invalidTimeField_doesNotCrash() {
        when(configService.getOrDefault(eq(ConfigKey.SCHEDULED_REPORT), any(ScheduledReportConfig.class)))
                .thenReturn(cfgEnabled("not-a-time"));

        /// 非法 time 字段应 warn 跳过，不抛异常、不调用 service。
        assertDoesNotThrow(() -> scheduler.tick());
        verify(scheduledReportService, never()).generateAndSend();
    }
}
