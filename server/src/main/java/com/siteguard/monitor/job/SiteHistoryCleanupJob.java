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
/// 使用数据库 bulk delete，每批最多删除 10000 行，控制事务大小和数据库压力。
@RequiredArgsConstructor
@Slf4j
public class SiteHistoryCleanupJob extends QuartzJobBean {

    /// 历史保留窗口：7 天
    private static final Duration RETENTION = Duration.ofDays(7);
    private static final int BATCH_SIZE = 10_000;

    private final SiteCheckHistoryRepository historyRepo;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        long threshold = System.currentTimeMillis() - RETENTION.toMillis();
        long totalDeleted = 0;
        int deleted;
        do {
            deleted = historyRepo.deleteBatchByCheckedAtLessThan(threshold, BATCH_SIZE);
            totalDeleted += deleted;
        } while (deleted == BATCH_SIZE);
        log.info("SiteHistoryCleanupJob deleted {} rows older than {} in batches of {}",
                totalDeleted, threshold, BATCH_SIZE);
    }
}
