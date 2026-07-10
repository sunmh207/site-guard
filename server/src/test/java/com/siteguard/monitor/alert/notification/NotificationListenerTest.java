package com.siteguard.monitor.alert.notification;

import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.notify.service.NotifyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock NotificationRepository repo;
    @Mock NotifyService notifyService;

    private NotificationListener listener;

    /// 监听器 save 两次（PENDING→SUCCESS/FAILED），共享同一个 row 实例。
    /// 这里在每次 save 时把 deliveryStatus 快照到 list，便于断言"第一次是 PENDING"。
    private final List<NotificationDeliveryStatus> statusesAtSave = new ArrayList<>();
    private final List<String> errorMessagesAtSave = new ArrayList<>();

    @BeforeEach
    void setUp() {
        listener = new NotificationListener(repo, notifyService);
    }

    private void resetSnapshots() {
        statusesAtSave.clear();
        errorMessagesAtSave.clear();
        when(repo.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            statusesAtSave.add(n.getDeliveryStatus());
            errorMessagesAtSave.add(n.getErrorMessage());
            return n;
        });
    }

    private NotificationEvent event(String bucket) {
        return new NotificationEvent(
                1L, "官网", "https://example.com",
                AlertKind.AVAILABILITY, AlertStatus.ABNORMAL,
                bucket, "msg-" + bucket, 1_700_000_000_000L);
    }

    private NotificationEvent eventWithSite(String bucket, String siteName, String siteUrl) {
        return new NotificationEvent(
                1L, siteName, siteUrl,
                AlertKind.AVAILABILITY, AlertStatus.ABNORMAL,
                bucket, "msg-" + bucket, 1_700_000_000_000L);
    }

    private NotificationEvent eventWithStatus(AlertStatus status, String bucket) {
        return new NotificationEvent(
                1L, "官网", "https://example.com",
                AlertKind.AVAILABILITY, status,
                bucket, "msg-" + bucket, 1_700_000_000_000L);
    }

    @Test
    void successPath_savesPendingThenSuccess() {
        resetSnapshots();

        listener.onNotification(event("DOWN"));

        // save 两次：第一次 PENDING，第二次 SUCCESS
        verify(repo, times(2)).save(any(Notification.class));
        assertEquals(NotificationDeliveryStatus.PENDING, statusesAtSave.get(0));
        assertEquals(NotificationDeliveryStatus.SUCCESS, statusesAtSave.get(1));
        // 成功路径下第二次保存 errorMessage 为 null
        assertNull(errorMessagesAtSave.get(1));

        // IM 消息应包含站点名称、域名、原始告警文本
        verify(notifyService).send(anyString(), argThat((String m) ->
                m.contains("官网") && m.contains("https://example.com") && m.contains("msg-DOWN")));
    }

    @Test
    void notifyServiceThrows_marksFailed_andCapturesErrorMessage() {
        resetSnapshots();
        doThrow(new RuntimeException("IM not configured"))
                .when(notifyService).send(anyString(), anyString());

        listener.onNotification(event("DOWN"));

        verify(repo, times(2)).save(any(Notification.class));
        assertEquals(NotificationDeliveryStatus.PENDING, statusesAtSave.get(0));
        assertEquals(NotificationDeliveryStatus.FAILED, statusesAtSave.get(1));
        assertEquals("IM not configured", errorMessagesAtSave.get(1));
    }

    /// 错误信息超过 1024 列长 → 截断保存，避免超长 message 撑爆 DB 列
    @Test
    void longErrorMessage_isTruncated() {
        resetSnapshots();
        String longMsg = "x".repeat(2000);
        doThrow(new RuntimeException(longMsg)).when(notifyService).send(anyString(), anyString());

        listener.onNotification(event("DOWN"));

        verify(repo, times(2)).save(any(Notification.class));
        assertEquals(NotificationDeliveryStatus.FAILED, statusesAtSave.get(1));
        assertNotNull(errorMessagesAtSave.get(1));
        assertEquals(1024, errorMessagesAtSave.get(1).length());
    }

    /// 监听器不抛异常：notifyService 抛出的异常已在内部 try/catch 处理
    @Test
    void doesNotRethrow() {
        resetSnapshots();
        doThrow(new RuntimeException("boom")).when(notifyService).send(anyString(), anyString());

        // 不期望抛
        listener.onNotification(event("DOWN"));

        // 即使失败也写完了 PENDING + FAILED
        verify(repo, times(2)).save(any(Notification.class));
    }

    /// onNotification 是 @Async 入口：验证方法签名可被 Spring 异步调用（void）
    @Test
    void asyncEntry_isVoidNoArgMethod() throws NoSuchMethodException {
        Method m = NotificationListener.class.getMethod("onNotification", NotificationEvent.class);
        assertEquals(void.class, m.getReturnType(), "@Async 方法必须返回 void");
    }

    @Test
    void retryCount_isInitializedToZero() {
        resetSnapshots();

        listener.onNotification(event("DOWN"));

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(repo, times(2)).save(cap.capture());
        // 首次写入 retryCount = 0（重试机制后续阶段实现）
        assertEquals(0, cap.getAllValues().get(0).getRetryCount());
        assertEquals(0, cap.getAllValues().get(1).getRetryCount());
    }

    /// ABNORMAL 通知：标题"⚠️ 告警通知" + 正文三行（图标+名称 / **网址**：URL / **消息**：body）
    /// 行间用 markdown 硬换行（"  \n"），确保飞书/钉钉/企微三家渲染器都按段成行
    @Test
    void abnormalStatus_usesWarningIconInTitleAndBody() {
        resetSnapshots();

        listener.onNotification(eventWithStatus(AlertStatus.ABNORMAL, "DOWN"));

        verify(notifyService).send("⚠️ 告警通知",
                "⚠️ 官网  \n**网址**：https://example.com  \n**消息**：msg-DOWN");
    }

    /// NORMAL（恢复）通知：标题"✅ 恢复通知" + 正文同样三行结构
    @Test
    void normalStatus_usesCheckmarkIconInTitleAndBody() {
        resetSnapshots();

        listener.onNotification(eventWithStatus(AlertStatus.NORMAL, "UP"));

        verify(notifyService).send("✅ 恢复通知",
                "✅ 官网  \n**网址**：https://example.com  \n**消息**：msg-UP");
    }

    /// 名称与 URL 同时存在 → 名称独占首行，URL 走"**网址**："标签行；行间硬换行
    @Test
    void imMessage_nameAndUrlOnSeparateLines() {
        resetSnapshots();

        listener.onNotification(event("DOWN"));

        verify(notifyService).send(anyString(), argThat((String m) ->
                m.startsWith("⚠️ 官网")
                        && m.contains("  \n**网址**：https://example.com")
                        && m.contains("  \n**消息**：msg-DOWN")));
    }

    /// 仅 URL → URL 直接放首行（无名称时不另起"网址："行，避免空标签噪音）
    @Test
    void urlOnly_inlineOnFirstLine() {
        resetSnapshots();

        listener.onNotification(eventWithSite("W7", null, "https://example.com"));

        verify(notifyService).send(anyString(), argThat((String m) ->
                m.startsWith("⚠️ https://example.com")
                        && m.contains("  \n**消息**：msg-W7")
                        && !m.contains("网址")));
    }

    /// 仅名称 → 仅名称行 + 消息行，无"网址："行
    @Test
    void nameOnly_isPlainText() {
        resetSnapshots();

        listener.onNotification(eventWithSite("W7", "官网", null));

        verify(notifyService).send(anyString(), argThat((String m) ->
                m.startsWith("⚠️ 官网")
                        && m.contains("  \n**消息**：msg-W7")
                        && !m.contains("网址")
                        && !m.contains("[官网](")
                        && !m.contains("()")));
    }

    /// 站点名称或 URL 缺失时优雅降级：仍然能产出可读消息
    @Test
    void missingSiteName_omittedFromMessage() {
        resetSnapshots();

        listener.onNotification(eventWithSite("W7", null, "https://example.com"));

        verify(notifyService).send(anyString(), argThat((String m) ->
                m.contains("⚠️ ") && m.contains("msg-W7") && m.contains("https://example.com")
                && !m.contains("[]") && !m.contains("()")));
    }

    @Test
    void missingSiteUrl_omittedFromMessage() {
        resetSnapshots();

        listener.onNotification(eventWithSite("W7", "官网", null));

        verify(notifyService).send(anyString(), argThat((String m) ->
                m.contains("⚠️ ") && m.contains("官网") && m.contains("msg-W7")
                && !m.contains("[]") && !m.contains("()")));
    }

    /// 站点上下文全缺失 → 图标行 + 消息行（图标末尾的尾随空格去掉，避免首行尾随空白）
    @Test
    void bothSiteFieldsNull_fallsBackToBareMessage() {
        resetSnapshots();

        var ev = new NotificationEvent(1L, null, null,
                AlertKind.AVAILABILITY, AlertStatus.ABNORMAL, "UP", "msg-UP", 1_700_000_000_000L);
        listener.onNotification(ev);

        verify(notifyService).send("⚠️ 告警通知", "⚠️  \n**消息**：msg-UP");
    }

    @Test
    void formatImText_pathCheck_doesNotAppendBucketHint() {
        var ev = new NotificationEvent(
                1L, "shop", "https://shop.example",
                AlertKind.PATH_CHECK, AlertStatus.ABNORMAL,
                "/api/orders",                              // bucket = pathKey
                "路径 /api/orders 返回 500，期望 200",
                1700000000000L);
        String text = NotificationListener.formatImText(ev);
        /// PATH_CHECK 的 message 体内已含路径（ABNORMAL: 路径 X...；NORMAL: 子路由 X...），
        /// 不再追加 `（路径：bucket）` hint 以免与 message 内容重复
        assertFalse(text.contains("（路径："), "got: " + text);
        assertTrue(text.contains("路径 /api/orders 返回 500"));
    }

    @Test
    void formatImText_availability_doesNotAppendHint() {
        var ev = new NotificationEvent(
                1L, "shop", "https://shop.example",
                AlertKind.AVAILABILITY, AlertStatus.ABNORMAL,
                "DOWN", "HTTP 500",
                1700000000000L);
        String text = NotificationListener.formatImText(ev);
        assertFalse(text.contains("（路径："), "got: " + text);
    }
}