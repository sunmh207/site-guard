package com.siteguard.monitor.service;

import com.siteguard.monitor.dto.DashboardResponse;
import com.siteguard.monitor.dto.DashboardSummaryDTO;
import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.notify.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/// 定时报告生成与发送。
///
/// 由 ScheduledReportScheduler 在每分钟 tick 调用，到点时：
/// 1. 拉取当前 dashboard 快照（摘要卡片 + 异常列表）
/// 2. 格式化为 IM 硬换行文本
/// 3. 通过 NotifyService 发到已配置的通知机器人
///
/// 复用 NotificationListener.formatImText 同样的 IM 硬换行约定（`  \n`），
/// 保证飞书/钉钉/企微渲染一致。
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledReportService {

    /// IM markdown 硬换行：两个尾随空格 + \n。裸 \n 在飞书/钉钉/企微会被折成空格。
    private static final String HARD_BREAK = "  \n";

    /// 日报时间戳标题 + "发现于" 行共用同一格式化器。与 dashboard"最后刷新"风格一致。
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SiteCheckService siteCheckService;
    private final NotifyService notifyService;

    /// 生成报告并通过 IM 机器人发送。任何异常向上抛出，由调用方决定是否更新去重标记。
    public void generateAndSend() {
        DashboardResponse dash = siteCheckService.getDashboard();
        if (dash == null) {
            throw new IllegalStateException("dashboard 数据为空");
        }
        String text = format(dash);
        notifyService.send("📊 站点监控日报", text);
        log.info("scheduled report sent, sites={}", dash.getSummary() != null ? dash.getSummary().getTotalSites() : 0);
    }

    /// 把 dashboard 数据拼成 IM 正文。抽成独立方法便于单测断言。
    public String format(DashboardResponse dash) {
        StringBuilder sb = new StringBuilder();

        String now = LocalDateTime.now().format(DATE_TIME);
        sb.append("📊 站点监控日报（").append(now).append("）");

        DashboardSummaryDTO summary = dash.getSummary();
        if (summary != null) {
            sb.append(HARD_BREAK).append(HARD_BREAK)
                    .append("【摘要】共 ").append(summary.getTotalSites()).append(" 站，健康 ")
                    .append(summary.getHealthyCount()).append("，异常 ")
                    .append(summary.getAbnormalCount()).append("，暂停 ")
                    .append(summary.getPausedCount());
            if (summary.getAvgResponseMs() != null) {
                sb.append("，平均响应 ").append(summary.getAvgResponseMs().intValue()).append("ms");
            }
        }

        sb.append(HARD_BREAK).append(HARD_BREAK).append("【异常列表】");

        var alerts = dash.getRecentAlerts();
        if (alerts == null || alerts.isEmpty()) {
            sb.append(HARD_BREAK).append("✅ 当前无异常");
        } else {
            for (int i = 0; i < alerts.size(); i++) {
                AlertDTO a = alerts.get(i);
                sb.append(HARD_BREAK);
                sb.append(i + 1).append(". ");
                if (a.getSiteName() != null && !a.getSiteName().isBlank()) {
                    sb.append(a.getSiteName());
                    if (a.getSiteUrl() != null && !a.getSiteUrl().isBlank()) {
                        sb.append(HARD_BREAK).append("   ").append(a.getSiteUrl());
                    }
                } else if (a.getSiteUrl() != null && !a.getSiteUrl().isBlank()) {
                    sb.append(a.getSiteUrl());
                } else {
                    sb.append("未知站点");
                }
                if (a.getMessage() != null && !a.getMessage().isBlank()) {
                    sb.append(HARD_BREAK).append("   ").append(a.getMessage());
                }
                sb.append(HARD_BREAK).append("   发现于 ").append(formatEpoch(a.getDetectedAt()));
            }
        }
        return sb.toString();
    }

    private String formatEpoch(Long epochMs) {
        if (epochMs == null) return "—";
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault()).format(DATE_TIME);
    }
}
