package com.siteguard.monitor.alert.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// DurationFormatter 把毫秒数格式化为「持续 X 秒 / X 分 Y 秒 / X 小时 Y 分 / —」文案。
class DurationFormatterTest {

    @Test
    void null_returnsDash() {
        assertEquals("—", DurationFormatter.format(null));
    }

    @Test
    void negative_returnsDash() {
        assertEquals("—", DurationFormatter.format(-1L));
    }

    @Test
    void zero_returnsZeroSeconds() {
        assertEquals("0 秒", DurationFormatter.format(0L));
    }

    @Test
    void subSecond_returnsZeroSeconds() {
        assertEquals("0 秒", DurationFormatter.format(999L));
    }

    @Test
    void oneSecond_returnsOneSecond() {
        assertEquals("1 秒", DurationFormatter.format(1_000L));
    }

    @Test
    void fiftyNineSeconds_staysInSeconds() {
        assertEquals("59 秒", DurationFormatter.format(59_500L));
    }

    @Test
    void sixtySeconds_promotesToMinutes() {
        assertEquals("1 分 00 秒", DurationFormatter.format(60_000L));
    }

    @Test
    void sixtyOneSeconds_roundsDownToMinute() {
        // 60_999 ms = 60.999s，toSeconds() 截断为 60，totalMinutes=1，sec=0
        assertEquals("1 分 00 秒", DurationFormatter.format(60_999L));
    }

    @Test
    void threeMinutes45Seconds_returnsMinutesAndSeconds() {
        assertEquals("3 分 45 秒", DurationFormatter.format(225_000L));
    }

    @Test
    void fiftyNineMinutes59Seconds_staysInMinutes() {
        assertEquals("59 分 59 秒", DurationFormatter.format(3_599_500L));
    }

    @Test
    void sixtyMinutes_promotesToHours() {
        assertEquals("1 小时 0 分", DurationFormatter.format(3_600_000L));
    }

    @Test
    void oneHour22Minutes_returnsHoursAndMinutes() {
        // 4_920_000 ms 刚好 1 小时 22 分；用 4_940_000L 是为了验证 toMinutes 取整不会因为多 20 秒改变"小时"档位
        assertEquals("1 小时 22 分", DurationFormatter.format(4_940_000L));
    }

    @Test
    void twelveHours_returnsHoursAndZeroMinutes() {
        assertEquals("12 小时 0 分", DurationFormatter.format(43_200_000L));
    }

    @Test
    void twentyFourHours_returnsTwentyFourHours() {
        assertEquals("24 小时 0 分", DurationFormatter.format(86_400_000L));
    }
}