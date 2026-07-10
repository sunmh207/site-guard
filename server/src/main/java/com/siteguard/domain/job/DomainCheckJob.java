package com.siteguard.domain.job;

import com.siteguard.domain.service.DomainCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

/// 每日凌晨 2 点触发的域名到期检测任务。
///
/// - @DisallowConcurrentExecution 避免重入
/// - 异常被吞掉并 log：单次失败不影响下一次调度
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class DomainCheckJob extends QuartzJobBean {

    private final DomainCheckService domainCheckService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            domainCheckService.checkAll();
        } catch (RuntimeException e) {
            // 单次失败不能影响下一次调度
            log.error("DomainCheckJob failed: {}", e.getMessage(), e);
        }
    }
}
