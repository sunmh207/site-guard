package com.siteguard.notify.sender.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.siteguard.notify.dto.TestWebhookResult;
import com.siteguard.notify.entity.ImRobot;
import com.siteguard.notify.enums.RobotPlatform;
import com.siteguard.notify.sender.ImSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/// 企业微信机器人发送器
///
/// 企业微信 webhook 不使用 secret（key 已在 URL 中）。
/// 消息类型：markdown（msgtype=markdown），content 支持完整 markdown 渲染
/// 响应：errcode=0 表示成功。
@Component
@Slf4j
public class WeChatWorkSender implements ImSender {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Override
    public RobotPlatform platform() {
        return RobotPlatform.WECHAT_WORK;
    }

    @Override
    public void send(ImRobot robot, String title, String message) {
        String body = buildMarkdownBody(title, message);
        String response = HttpUtil.post(robot.getWebhookUrl(), body);
        log.debug("企微发送响应: {}", response);
        // 契约：发送失败抛 RuntimeException，由 NotifyService 捕获并向上抛出
        TestWebhookResult r = parseResponse(response);
        if (!Boolean.TRUE.equals(r.getSuccess())) {
            throw new RuntimeException("企微发送失败: " + r.getMessage());
        }
    }

    @Override
    public TestWebhookResult testWebhook(String webhookUrl, String secret) {
        String body = "{\"msgtype\":\"text\",\"text\":{\"content\":\"Webhook 连接测试成功\"}}";
        String response;
        try {
            response = HttpUtil.post(webhookUrl, body);
        }
        catch (Exception e) {
            log.warn("企微 webhook 请求异常: {}", e.getMessage());
            return TestWebhookResult.fail("请求异常: " + e.getMessage());
        }
        return parseResponse(response);
    }

    /// 构造企微 markdown body：title 仅用于日志追溯，content 才是真实渲染内容（首行用粗体模拟标题）
    private String buildMarkdownBody(String title, String message) {
        String content;
        String safeMessage = message == null ? "" : message;
        if (StrUtil.isNotBlank(title)) {
            content = "**" + title + "**\n\n" + safeMessage;
        } else {
            content = safeMessage;
        }
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("content", content);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");
        body.put("markdown", markdown);
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("企微 body 序列化失败", e);
        }
    }

    TestWebhookResult parseResponse(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            return TestWebhookResult.fail("响应为空");
        }
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode codeNode = root.get("errcode");
            if (codeNode == null || codeNode.isNull()) {
                return TestWebhookResult.fail("无法识别的响应格式");
            }
            int code = codeNode.asInt();
            String errmsg = root.path("errmsg").asString("");
            if (code == 0) {
                return TestWebhookResult.ok(StrUtil.isNotBlank(errmsg) ? errmsg : "ok");
            }
            return TestWebhookResult.fail(StrUtil.isNotBlank(errmsg) ? errmsg : ("errcode=" + code));
        }
        catch (Exception e) {
            return TestWebhookResult.fail("响应格式异常，无法解析");
        }
    }
}