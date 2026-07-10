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

class WeChatWorkSenderTest {

    private final WeChatWorkSender sender = new WeChatWorkSender();

    @Test
    void platform_returnsWechatWork() {
        assertEquals(RobotPlatform.WECHAT_WORK, sender.platform());
    }

    @Test
    void send_postsMarkdownWithTitlePrefix() {
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            String[] capturedBody = new String[1];
            http.when(() -> HttpUtil.post(anyString(), anyString())).thenAnswer(inv -> {
                capturedBody[0] = inv.getArgument(1);
                return "{\"errcode\":0,\"errmsg\":\"ok\"}";
            });

            var robot = new ImRobot();
            robot.setPlatform(RobotPlatform.WECHAT_WORK);
            robot.setWebhookUrl("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx");

            sender.send(robot, "告警通知", "⚠️ 官网当前不可用");

            assertNotNull(capturedBody[0]);
            // 企微 markdown：msgtype=markdown，content 把 title 渲染为粗体首行，紧跟正文
            assertTrue(capturedBody[0].contains("\"msgtype\":\"markdown\""), "应使用 markdown 类型, 实际: " + capturedBody[0]);
            assertTrue(capturedBody[0].contains("**告警通知**"), "title 应作为粗体首行, 实际: " + capturedBody[0]);
            assertTrue(capturedBody[0].contains("⚠️ 官网当前不可用"), "content 应包含正文, 实际: " + capturedBody[0]);
            assertFalse(capturedBody[0].contains("\"msgtype\":\"text\""), "不应再使用 text 类型");
        }
    }

    @Test
    void send_errcodeNonZero_throws() {
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString())).thenReturn("{\"errcode\":40001,\"errmsg\":\"invalid token\"}");

            var robot = new ImRobot();
            robot.setPlatform(RobotPlatform.WECHAT_WORK);
            robot.setWebhookUrl("https://x");

            assertThrows(RuntimeException.class, () -> sender.send(robot, "t", "hi"));
        }
    }

    @Test
    void testWebhook_errcodeZero_returnsOk() {
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString())).thenReturn("{\"errcode\":0,\"errmsg\":\"\"}");

            var result = sender.testWebhook("https://x", null);
            assertTrue(result.getSuccess());
        }
    }

    @Test
    void testWebhook_errcodeNonZero_returnsFail() {
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString())).thenReturn("{\"errcode\":40001,\"errmsg\":\"invalid token\"}");

            var result = sender.testWebhook("https://x", null);
            assertFalse(result.getSuccess());
            assertEquals("invalid token", result.getMessage());
        }
    }
}