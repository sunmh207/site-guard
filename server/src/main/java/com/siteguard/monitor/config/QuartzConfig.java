package com.siteguard.monitor.config;

import com.siteguard.domain.job.DomainCheckJob;
import com.siteguard.monitor.job.AlertDetectionJob;
import com.siteguard.monitor.job.SiteCheckJob;
import com.siteguard.monitor.job.SiteHistoryCleanupJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// 注册 Quartz Job 与对应 Trigger。
///
/// 调度：
/// - siteCheckJob        ：每分钟整点（"0 * * * * ?"）
/// - alertDetectionJob   ：每分钟 :30（"30 * * * * ?"，与探测错开 30s）
/// - domainCheckJob      ：每天 02:00（"0 0 2 * * ?"）
/// - siteHistoryCleanupJob：每天 03:00（"0 0 3 * * ?"）
///
/// 表结构由 Flyway V20260630112000 提供；JDBC store 由 application.yaml 的
/// spring.quartz.job-store-type=jdbc 启用。
@Configuration
public class QuartzConfig {

    @Bean
    public JobDetail siteCheckJobDetail() {
        return JobBuilder.newJob(SiteCheckJob.class)
                .withIdentity("siteCheckJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger siteCheckTrigger(JobDetail siteCheckJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(siteCheckJobDetail)
                .withIdentity("siteCheckTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 * * * * ?"))
                .build();
    }

    @Bean
    public JobDetail alertDetectionJobDetail() {
        return JobBuilder.newJob(AlertDetectionJob.class)
                .withIdentity("alertDetectionJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger alertDetectionTrigger(JobDetail alertDetectionJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(alertDetectionJobDetail)
                .withIdentity("alertDetectionTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("30 * * * * ?"))
                .build();
    }

    @Bean
    public JobDetail siteHistoryCleanupJobDetail() {
        return JobBuilder.newJob(SiteHistoryCleanupJob.class)
                .withIdentity("siteHistoryCleanupJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger siteHistoryCleanupTrigger(JobDetail siteHistoryCleanupJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(siteHistoryCleanupJobDetail)
                .withIdentity("siteHistoryCleanupTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 3 * * ?"))
                .build();
    }

    @Bean
    public JobDetail domainCheckJobDetail() {
        return JobBuilder.newJob(DomainCheckJob.class)
                .withIdentity("domainCheckJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger domainCheckTrigger(JobDetail domainCheckJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(domainCheckJobDetail)
                .withIdentity("domainCheckTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 2 * * ?"))
                .build();
    }
}