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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class DingTalkSenderTest {

    private final DingTalkSender sender = new DingTalkSender();

    @Test
    void platform_returnsDingtalk() {
        assertEquals(RobotPlatform.DINGTALK, sender.platform());
    }

    @Test
    void send_withoutSecret_postsMarkdownJson() {
        var robot = new ImRobot();
        robot.setPlatform(RobotPlatform.DINGTALK);
        robot.setWebhookUrl("https://oapi.dingtalk.com/robot/send?access_token=abc");

        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            String[] capturedBody = new String[1];
            http.when(() -> HttpUtil.post(eq("https://oapi.dingtalk.com/robot/send?access_token=abc"), anyString()))
                .thenAnswer(inv -> {
                    capturedBody[0] = inv.getArgument(1);
                    return "{\"errcode\":0,\"errmsg\":\"ok\"}";
                });

            sender.send(robot, "告警通知", "⚠️ 官网当前不可用");

            // 钉钉 markdown：msgtype=markdown，title 与 text 字段透传
            assertNotNull(capturedBody[0]);
            assertTrue(capturedBody[0].contains("\"msgtype\":\"markdown\""), "应使用 markdown 类型, 实际: " + capturedBody[0]);
            assertTrue(capturedBody[0].contains("\"title\":\"告警通知\""), "应携带 title, 实际: " + capturedBody[0]);
            assertTrue(capturedBody[0].contains("\"text\":\"⚠️ 官网当前不可用\""), "应携带 text, 实际: " + capturedBody[0]);
            assertFalse(capturedBody[0].contains("\"msgtype\":\"text\""), "不应再使用 text 类型");
        }
    }

    @Test
    void send_withSecret_appendsTimestampAndSign() {
        var robot = new ImRobot();
        robot.setPlatform(RobotPlatform.DINGTALK);
        robot.setWebhookUrl("https://oapi.dingtalk.com/robot/send?access_token=abc");
        robot.setSecret("SEC");

        // 钉钉签名约定: sign = URLEncode(Base64(HmacSHA256(timestamp + "\n" + secret, secret)))
        // 这里只校验 URL 拼接 + body 内容，不再复算签名
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            String[] capturedUrl = new String[1];
            String[] capturedBody = new String[1];
            http.when(() -> HttpUtil.post(anyString(), anyString())).thenAnswer(inv -> {
                capturedUrl[0] = inv.getArgument(0);
                capturedBody[0] = inv.getArgument(1);
                return "{\"errcode\":0,\"errmsg\":\"ok\"}";
            });

            sender.send(robot, "告警通知", "⚠️ 官网当前不可用");

            assertNotNull(capturedUrl[0]);
            assertTrue(capturedUrl[0].startsWith("https://oapi.dingtalk.com/robot/send?access_token=abc&timestamp="),
                    "应追加 timestamp 参数, 实际 URL: " + capturedUrl[0]);
            assertTrue(capturedUrl[0].contains("&sign="), "应追加 sign 参数");
            assertTrue(capturedBody[0].contains("\"msgtype\":\"markdown\""));
            assertTrue(capturedBody[0].contains("\"title\":\"告警通知\""));
        }
    }

    @Test
    void send_errcodeNonZero_throws() {
        var robot = new ImRobot();
        robot.setPlatform(RobotPlatform.DINGTALK);
        robot.setWebhookUrl("https://x");

        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString())).thenReturn("{\"errcode\":40001,\"errmsg\":\"invalid token\"}");

            assertThrows(RuntimeException.class, () -> sender.send(robot, "t", "hi"));
        }
    }

    @Test
    void testWebhook_errcodeZero_returnsOk() {
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString())).thenReturn("{\"errcode\":0,\"errmsg\":\"ok\"}");

            var result = sender.testWebhook("https://x", null);
            assertTrue(result.getSuccess());
            assertEquals("ok", result.getMessage());
        }
    }

    @Test
    void testWebhook_errcodeNonZero_returnsFail() {
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString())).thenReturn("{\"errcode\":310000,\"errmsg\":\"ip not in whitelist\"}");

            var result = sender.testWebhook("https://x", null);
            assertFalse(result.getSuccess());
            assertEquals("ip not in whitelist", result.getMessage());
        }
    }

    @Test
    void testWebhook_invalidJson_returnsFail() {
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString())).thenReturn("not-json");

            var result = sender.testWebhook("https://x", null);
            assertFalse(result.getSuccess());
        }
    }
}