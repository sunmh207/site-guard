package com.siteguard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/// 全局异步任务执行器：用于 @Async 事件监听（NotificationListener.onNotification）。
///
/// 使用虚拟线程（Java 21+）：每个任务独占一个虚拟线程，
/// 阻塞 IO（HTTP 调用 IM 渠道）期间释放载体线程，提升吞吐。
///
/// 与 SiteCheckServiceImpl 风格一致——项目内首次启用 @Async，
/// 后续其他 @Async 方法若不指定 executor 默认也会使用本 bean。
@Configuration
public class AsyncExecutorConfig {

    @Bean(name = "applicationEventTaskExecutor")
    public AsyncTaskExecutor applicationEventTaskExecutor() {
        var executor = new SimpleAsyncTaskExecutor("app-event-");
        executor.setVirtualThreads(true);
        return executor;
    }
}