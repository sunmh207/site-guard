package com.siteguard.monitor.job;

import com.siteguard.monitor.alert.detection.AlertDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

/// 每分钟 :30 触发的告警边沿检测任务。
///
/// 与 SiteCheckJob（每分钟 :00）错开 30s：
///   - 探测完成 → 检测拿到最新 site 快照 → 边沿对比 → 发通知
///   - 错开避免单点同时打满 DB / IM 渠道
///
/// - @DisallowConcurrentExecution 避免重入
/// - 异常被吞掉并 log：单次失败不能影响下一次调度
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class AlertDetectionJob extends QuartzJobBean {

    private final AlertDetectionService alertDetectionService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            alertDetectionService.detectAll();
        } catch (RuntimeException e) {
            log.error("AlertDetectionJob failed: {}", e.getMessage(), e);
        }
    }
}