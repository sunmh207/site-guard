package com.siteguard.monitor.job;

import com.siteguard.monitor.repository.SitePathCheckHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.Duration;

///
/// 子路由检测历史清理 job。
///
/// 镜像 SiteHistoryCleanupJob：删除超过 7 天的 site_path_check_history 记录。
/// 使用数据库 bulk delete，每批最多删除 10000 行，控制事务大小和数据库压力。
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class SitePathHistoryCleanupJob extends QuartzJobBean {

    /// 历史保留窗口：7 天（与 SiteHistoryCleanupJob 一致）
    private static final Duration RETENTION = Duration.ofDays(7);
    private static final int BATCH_SIZE = 10_000;

    private final SitePathCheckHistoryRepository historyRepo;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        long threshold = System.currentTimeMillis() - RETENTION.toMillis();
        long totalDeleted = 0;
        int deleted;
        do {
            deleted = historyRepo.deleteBatchByCheckedAtLessThan(threshold, BATCH_SIZE);
            totalDeleted += deleted;
        } while (deleted == BATCH_SIZE);
        log.info("SitePathHistoryCleanupJob deleted {} rows older than {} in batches of {}",
                totalDeleted, threshold, BATCH_SIZE);
    }
}
