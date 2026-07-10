package com.siteguard.notify.sender.impl;

import cn.hutool.http.HttpUtil;
import com.siteguard.notify.entity.ImRobot;
import com.siteguard.notify.enums.RobotPlatform;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

class FeishuSenderTest {

    private final FeishuSender sender = new FeishuSender();

    @Test
    void platform_returnsFeishu() {
        assertEquals(RobotPlatform.FEISHU, sender.platform());
    }

    @Test
    void send_withoutSecret_postsInteractiveCard() {
        var robot = new ImRobot();
        robot.setPlatform(RobotPlatform.FEISHU);
        robot.setWebhookUrl("https://open.feishu.cn/hook/xxx");

        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            String[] capturedBody = new String[1];
            http.when(() -> HttpUtil.post(anyString(), anyString())).thenAnswer(inv -> {
                capturedBody[0] = inv.getArgument(1);
                return "{\"code\":0,\"msg\":\"success\"}";
            });

            sender.send(robot, "告警通知", "⚠️ 官网当前不可用");

            assertNotNull(capturedBody[0]);
            // 飞书 interactive 卡片：msg_type=interactive，header.title 来自 title 参数
            assertTrue(capturedBody[0].contains("\"msg_type\":\"interactive\""), "应使用 interactive 类型, 实际: " + capturedBody[0]);
            assertTrue(capturedBody[0].contains("\"tag\":\"plain_text\""), "header.title 必须是 plain_text, 实际: " + capturedBody[0]);
            assertTrue(capturedBody[0].contains("\"content\":\"告警通知\""), "header.title.content 应携带 title, 实际: " + capturedBody[0]);
            assertTrue(capturedBody[0].contains("\"tag\":\"markdown\""), "elements 应包含 markdown 标签, 实际: " + capturedBody[0]);
            assertTrue(capturedBody[0].contains("\"content\":\"⚠️ 官网当前不可用\""), "elements[markdown].content 应携带正文, 实际: " + capturedBody[0]);
            assertFalse(capturedBody[0].contains("\"msg_type\":\"text\""), "不应再使用 text 类型");
        }
    }

    @Test
    void send_withSecret_appendsTimestampAndSign() {
        var robot = new ImRobot();
        robot.setPlatform(RobotPlatform.FEISHU);
        robot.setWebhookUrl("https://open.feishu.cn/hook/xxx");
        robot.setSecret("SEC");

        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            String[] capturedUrl = new String[1];
            String[] capturedBody = new String[1];
            http.when(() -> HttpUtil.post(anyString(), anyString())).thenAnswer(inv -> {
                capturedUrl[0] = inv.getArgument(0);
                capturedBody[0] = inv.getArgument(1);
                return "{\"code\":0,\"msg\":\"ok\"}";
            });

            sender.send(robot, "告警通知", "✅ 官网已恢复");

            assertNotNull(capturedUrl[0]);
            assertTrue(capturedUrl[0].startsWith("https://open.feishu.cn/hook/xxx?timestamp="),
                    "应追加 timestamp 参数, 实际 URL: " + capturedUrl[0]);
            assertTrue(capturedUrl[0].contains("&sign="), "应追加 sign 参数");
            assertTrue(capturedBody[0].contains("\"msg_type\":\"interactive\""));
        }
    }

    @Test
    void send_codeNonZero_throws() {
        var robot = new ImRobot();
        robot.setPlatform(RobotPlatform.FEISHU);
        robot.setWebhookUrl("https://x");

        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString())).thenReturn("{\"code\":190002,\"msg\":\"token invalid\"}");

            assertThrows(RuntimeException.class, () -> sender.send(robot, "t", "hi"));
        }
    }

    @Test
    void testWebhook_codeZero_returnsOk() {
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString())).thenReturn("{\"code\":0,\"msg\":\"success\"}");

            var result = sender.testWebhook("https://x", null);
            assertTrue(result.getSuccess());
        }
    }

    @Test
    void testWebhook_codeNonZero_returnsFail() {
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString())).thenReturn("{\"code\":190002,\"msg\":\"token invalid\"}");

            var result = sender.testWebhook("https://x", null);
            assertFalse(result.getSuccess());
            assertEquals("token invalid", result.getMessage());
        }
    }
}