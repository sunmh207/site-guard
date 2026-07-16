package com.siteguard.monitor.alert.notification;

import java.time.Duration;

/// 把"持续毫秒数"格式化为人眼可读的运维文案。
///   < 1 分钟  → "X 秒"
///   < 1 小时  → "M 分 SS 秒"
///   ≥ 1 小时  → "H 小时 M 分"
///   null/负数  → "—"（防御性兜底）
public final class DurationFormatter {

    private DurationFormatter() {}

    public static String format(Long durationMs) {
        if (durationMs == null || durationMs < 0) {
            return "—";
        }
        Duration d = Duration.ofMillis(durationMs);
        long totalSeconds = d.toSeconds();

        if (totalSeconds < 60) {
            return totalSeconds + " 秒";
        }
        long totalMinutes = d.toMinutes();
        if (totalMinutes < 60) {
            long sec = totalSeconds - totalMinutes * 60;
            return totalMinutes + " 分 " + String.format("%02d", sec) + " 秒";
        }
        long hours = d.toHours();
        long min = totalMinutes - hours * 60;
        return hours + " 小时 " + min + " 分";
    }
}