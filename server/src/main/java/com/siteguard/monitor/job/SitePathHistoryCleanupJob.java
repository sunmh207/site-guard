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
/// 数据规模与 site_check_history 相当（每条路径规则每次探测一行），同样不分批。
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class SitePathHistoryCleanupJob extends QuartzJobBean {

    /// 历史保留窗口：7 天（与 SiteHistoryCleanupJob 一致）
    private static final Duration RETENTION = Duration.ofDays(7);

    private final SitePathCheckHistoryRepository historyRepo;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        long threshold = System.currentTimeMillis() - RETENTION.toMillis();
        long deleted = historyRepo.deleteByCheckedAtLessThan(threshold);
        log.info("SitePathHistoryCleanupJob deleted {} rows older than {}", deleted, threshold);
    }
}
