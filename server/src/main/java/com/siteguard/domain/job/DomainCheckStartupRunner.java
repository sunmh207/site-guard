package com.siteguard.domain.job;

import com.siteguard.domain.service.DomainCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/// 应用启动后立即异步跑一次域名到期检测。
///
/// - 实现 ApplicationRunner：Spring 上下文就绪后才触发，不会阻塞应用启动
/// - 用 Executor 异步执行：生产用虚拟线程池（不阻塞 runner 回调），
///   测试可注入同步 executor 让测试确定性强
/// - 异常被吞掉并 log：启动期检测失败不影响应用正常运行
/// - 每日 02:00 的 Quartz 任务保持不变，仍负责后续的周期性更新
@Component
@Slf4j
public class DomainCheckStartupRunner implements ApplicationRunner {

    /// 默认异步 executor：每任务一个虚拟线程，与 service.checkAll 的并发模型一致
    private static final Executor VIRTUAL_THREAD = Executors.newVirtualThreadPerTaskExecutor();

    private final DomainCheckService domainCheckService;
    private final Executor executor;

    /// 生产构造器：用虚拟线程池异步跑
    @Autowired
    public DomainCheckStartupRunner(DomainCheckService domainCheckService) {
        this(domainCheckService, VIRTUAL_THREAD);
    }

    /// 测试构造器：注入同步 executor 让 verify 立即可见
    DomainCheckStartupRunner(DomainCheckService domainCheckService, Executor executor) {
        this.domainCheckService = domainCheckService;
        this.executor = executor;
    }

    @Override
    public void run(ApplicationArguments args) {
        executor.execute(() -> {
            try {
                log.info("Running startup domain check");
                domainCheckService.checkAll();
                log.info("Startup domain check completed");
            } catch (RuntimeException e) {
                // 启动期检测失败不能影响应用启动
                log.error("Startup domain check failed: {}", e.getMessage(), e);
            }
        });
    }
}