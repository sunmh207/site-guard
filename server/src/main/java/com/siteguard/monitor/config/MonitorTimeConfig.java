package com.siteguard.monitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/// 集中放置 monitor 模块需注入的时间相关 bean。
///
/// 当前仅提供默认 [Clock]。测试可在构造 source 时直接传 `Clock.fixed(...)`，
/// 不再依赖 `System.currentTimeMillis()` 制造 flaky 测试。
@Configuration
public class MonitorTimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
