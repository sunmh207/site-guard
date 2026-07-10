package com.siteguard.monitor.alert.notification;

import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.notify.service.NotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// 边沿通知事件监听器：异步落库 + 投递 IM 渠道。
///
/// 流程：
/// 1. 先以 PENDING 状态写入 notification 表（崩溃也能审计）
/// 2. 调 NotifyService 发送
/// 3. 成功 → SUCCESS，失败 → FAILED 并写入 errorMessage
///
/// @Async 让发送不阻塞检测 tick；@Transactional 保证两步写要么一起成功要么回滚。
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1024;

    /// ABNORMAL 通知前缀：警告图标（飞书/钉钉/企微均支持 emoji 原生渲染）
    private static final String ABNORMAL_ICON = "⚠️ ";
    /// NORMAL（恢复）通知前缀：绿色对勾
    private static final String NORMAL_ICON = "✅ ";

    private final NotificationRepository repo;
    private final NotifyService notifyService;

    public NotificationListener(NotificationRepository repo, NotifyService notifyService) {
        this.repo = repo;
        this.notifyService = notifyService;
    }

    @Async("applicationEventTaskExecutor")
    @EventListener
    @Transactional
    public void onNotification(NotificationEvent ev) {
        // 落库保留原始告警文本（无站点前缀），便于聚合/检索/审计
        Notification row = Notification.builder()
                .siteId(ev.siteId())
                .alertKind(ev.alertKind())
                .status(ev.status())
                .bucket(ev.bucket())
                .message(ev.message())
                .sentAt(ev.detectedAt())
                .deliveryStatus(NotificationDeliveryStatus.PENDING)
                .retryCount(0)
                .build();
        repo.save(row);

        // IM 文本：状态图标 + 多行站点上下文（名称/网址/消息三段式）
        String title = formatImTitle(ev.status());
        String imText = formatImText(ev);

        try {
            notifyService.send(title, imText);
            row.setDeliveryStatus(NotificationDeliveryStatus.SUCCESS);
            row.setErrorMessage(null);
        } catch (RuntimeException e) {
            log.warn("notification send failed for site {} kind {} bucket {}",
                    ev.siteId(), ev.alertKind(), ev.bucket(), e);
            row.setDeliveryStatus(NotificationDeliveryStatus.FAILED);
            row.setErrorMessage(truncate(e.getMessage(), ERROR_MESSAGE_MAX_LENGTH));
        }
        repo.save(row);
    }

    /// 拼装 IM 标题：ABNORMAL → ⚠️ 告警通知；NORMAL → ✅ 恢复通知
    static String formatImTitle(AlertStatus status) {
        return status == AlertStatus.NORMAL ? NORMAL_ICON + "恢复通知" : ABNORMAL_ICON + "告警通知";
    }

    /// 拼装 IM 正文：多行 markdown，标题栏、网址栏、消息栏各占一行，便于在 IM 端一眼扫读。
    ///
    /// 站点栏四种形态：
    /// - 名称 + URL → 第一行 `icon name`；第二行 `**网址**：url`
    /// - 仅有 URL   → 第一行 `icon url`（无名称时不另起"网址："行，避免空标签噪音）
    /// - 仅有名称   → 第一行 `icon name`
    /// - 都缺失     → 第一行 `icon`（去掉 icon 末尾的尾随空格，单独占一行）
    ///
    /// 消息栏始终为最后一行 `**消息**：<原始告警>`，PATH_CHECK 的消息体里已经包含路径信息
    ///（ABNORMAL: `路径 X 返回 500，期望 200`；NORMAL: `子路由 X 已恢复（期望 200）`），
    /// 不再追加 `（路径：bucket）` hint 以免与 message 内的路径重复。
    ///
    /// 行间分隔用 markdown 硬换行（`  \n`，两个尾随空格 + 换行）。飞书/钉钉/企微三家 markdown 渲染器
    /// 都会把裸 `\n` 当成 soft break 折叠成空格，必须用硬换行才能保证每段独立成行。
    static String formatImText(NotificationEvent ev) {
        /// 飞书/钉钉/企微 markdown 硬换行：两个尾随空格 + \n，裸 \n 在渲染时会被折成空格
        final String HARD_BREAK = "  \n";

        String icon = ev.status() == AlertStatus.NORMAL ? NORMAL_ICON : ABNORMAL_ICON;
        /// icon 末尾的空格用于与站点名称/URL 分隔；站点上下文全缺失时去掉，避免首行尾随空白
        String iconNoSpace = icon.substring(0, icon.length() - 1);
        String name = ev.siteName();
        String url = ev.siteUrl();
        boolean hasName = name != null && !name.isBlank();
        boolean hasUrl = url != null && !url.isBlank();

        String body = ev.message() == null ? "" : ev.message();

        StringBuilder sb = new StringBuilder();
        if (hasName) {
            sb.append(icon).append(name);
            if (hasUrl) {
                sb.append(HARD_BREAK).append("**网址**：").append(url);
            }
        } else if (hasUrl) {
            sb.append(icon).append(url);
        } else {
            sb.append(iconNoSpace);
        }
        sb.append(HARD_BREAK).append("**消息**：").append(body);

        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}