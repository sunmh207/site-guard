package com.siteguard.monitor.scheduler;

import com.siteguard.system.config.ScheduledReportConfig;
import com.siteguard.system.entity.SystemConfig;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.repository.SystemConfigRepository;
import com.siteguard.system.service.ConfigService;
import com.siteguard.monitor.service.ScheduledReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/// 定时报告调度器。
///
/// 每分钟 tick 一次，判断三个条件同时满足才触发：
///   1. enabled = true
///   2. 当前时刻匹配配置的 HH:mm（±1 分钟容差，避免 fixedRate 跨越分钟边界漏发）
///   3. 今日尚未发送（去重，避免重启后重复发送）
///
/// 发送失败由 ScheduledReportService 向上抛 RuntimeException → 本层 catch 记 warn，
/// **不**更新 lastSentDate，下一轮 tick 重试（"至少一次"语义，避免漏发）。
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledReportScheduler {

    /// 时刻匹配容差（分钟）。fixedRate 触发点可能刚好跨在目标分钟前后，容差避免漏发。
    private static final int MATCH_TOLERANCE_MINUTES = 1;

    /// 内部去重标记的 config_key。非用户配置，仅供调度器内部持久化当日已发状态。
    private static final String LAST_SENT_KEY = "scheduled_report_last_sent";

    private final ConfigService configService;
    private final SystemConfigRepository systemConfigRepository;
    private final ScheduledReportService scheduledReportService;

    /// 内存层 lastSentDate：常规路径零 DB。volatile 保证多线程可见（仅单线程写 volatile 语义足够）。
    private volatile LocalDate lastSentDate;

    @Scheduled(fixedRate = 60_000)
    public void tick() {
        ScheduledReportConfig cfg = configService.getOrDefault(ConfigKey.SCHEDULED_REPORT,
                ScheduledReportConfig.builder().enabled(false).time("08:00").build());
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            return;
        }
        if (!matchesTime(cfg.getTime())) {
            return;
        }
        if (isSentToday()) {
            return;
        }
        try {
            scheduledReportService.generateAndSend();
        } catch (RuntimeException e) {
            /// 发送失败 → 不更新 lastSentDate，下一轮 tick 重试。
            log.warn("scheduled report send failed, will retry next tick: {}", e.getMessage());
            return;
        }
        /// 发送成功 → 更新去重标记（内存 + 持久层）。
        markSentToday();
    }

    /// 当前本地时刻是否匹配配置的 HH:mm（±MATCH_TOLERANCE_MINUTES 容差）。
    private boolean matchesTime(String time) {
        if (time == null || time.isBlank()) {
            return false;
        }
        LocalTime target;
        try {
            target = LocalTime.parse(time);
        } catch (DateTimeParseException e) {
            log.warn("scheduled_report time 字段非法，无法解析: {}", time);
            return false;
        }
        LocalTime now = LocalTime.now();
        long diffMinutes = java.time.Duration.between(target, now).toMinutes();
        return Math.abs(diffMinutes) <= MATCH_TOLERANCE_MINUTES;
    }

    /// 综合判定今日是否已发送：先检查内存层，未命中再查持久层（重启恢复场景）。
    private boolean isSentToday() {
        LocalDate today = LocalDate.now();
        if (lastSentDate != null && lastSentDate.equals(today)) {
            return true;
        }
        return loadLastSentDate().equals(today);
    }

    /// 同时更新内存层 + 持久层。持久层仅用于跨重启去重。
    private void markSentToday() {
        LocalDate today = LocalDate.now();
        lastSentDate = today;
        persistLastSentDate(today);
    }

    private LocalDate loadLastSentDate() {
        Optional<SystemConfig> entity = systemConfigRepository.findByConfigKey(LAST_SENT_KEY);
        if (entity.isEmpty()) {
            return LocalDate.MIN;
        }
        try {
            return LocalDate.parse(entity.get().getConfigValue());
        } catch (DateTimeParseException e) {
            return LocalDate.MIN;
        }
    }

    private void persistLastSentDate(LocalDate date) {
        SystemConfig entity = systemConfigRepository.findByConfigKey(LAST_SENT_KEY)
                .orElseGet(SystemConfig::new);
        entity.setConfigKey(LAST_SENT_KEY);
        entity.setConfigValue(date.toString());
        systemConfigRepository.save(entity);
    }
}
