package com.siteguard.monitor.job;

import com.siteguard.monitor.service.SiteCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

/// 每分钟触发一次的站点探活任务。
///
/// - @DisallowConcurrentExecution 避免重入（在多实例场景下由 Quartz 集群锁保证）
/// - 异常被吞掉并 log：单次失败不影响下一次调度
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class SiteCheckJob extends QuartzJobBean {

    private final SiteCheckService siteCheckService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            siteCheckService.checkAll();
        } catch (RuntimeException e) {
            // 单次失败不能影响下一次调度
            log.error("SiteCheckJob failed: {}", e.getMessage(), e);
        }
    }
}
