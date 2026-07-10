package com.siteguard.monitor.job;

import com.siteguard.monitor.repository.SiteCheckHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.Duration;

/// 每天凌晨 3 点触发，删除 7 天前的所有历史。
///
/// 不分批：1000 站 × 7 天 × 1440 条/天 = 1000 万行在单 DELETE 下仍可控。
/// 数据规模再大时改成分批。
@RequiredArgsConstructor
@Slf4j
public class SiteHistoryCleanupJob extends QuartzJobBean {

    /// 历史保留窗口：7 天
    private static final Duration RETENTION = Duration.ofDays(7);

    private final SiteCheckHistoryRepository historyRepo;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        long threshold = System.currentTimeMillis() - RETENTION.toMillis();
        long deleted = historyRepo.deleteByCheckedAtLessThan(threshold);
        log.info("SiteHistoryCleanupJob deleted {} rows older than {}", deleted, threshold);
    }
}
