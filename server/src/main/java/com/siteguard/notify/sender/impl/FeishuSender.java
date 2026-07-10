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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 飞书机器人发送器
///
/// - 加签：secret 非空时按飞书官方算法：sign = Base64(HmacSHA256("<ts>\n<secret>", secret))
///   timestamp 单位为秒（区别于钉钉的毫秒），且不需 URLEncoder（区别于钉钉）
/// - 无 secret：直接 POST
/// - 消息类型：interactive 卡片（msg_type=interactive），header.title 用通知标题，elements[markdown] 渲染正文
/// - 响应：code=0 表示成功
@Component
@Slf4j
public class FeishuSender implements ImSender {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Override
    public RobotPlatform platform() {
        return RobotPlatform.FEISHU;
    }

    @Override
    public void send(ImRobot robot, String title, String message) {
        String url = appendSignIfNeeded(robot.getWebhookUrl(), robot.getSecret());
        String body = buildCardBody(title, message);
        String response = HttpUtil.post(url, body);
        log.debug("飞书发送响应: {}", response);
        // 契约：发送失败抛 RuntimeException，由 NotifyService 捕获并向上抛出
        TestWebhookResult r = parseResponse(response);
        if (!Boolean.TRUE.equals(r.getSuccess())) {
            throw new RuntimeException("飞书发送失败: " + r.getMessage());
        }
    }

    @Override
    public TestWebhookResult testWebhook(String webhookUrl, String secret) {
        String url = appendSignIfNeeded(webhookUrl, secret);
        String body = "{\"msg_type\":\"text\",\"content\":{\"text\":\"Webhook 连接测试成功\"}}";
        String response;
        try {
            response = HttpUtil.post(url, body);
        }
        catch (Exception e) {
            log.warn("飞书 webhook 请求异常: {}", e.getMessage());
            return TestWebhookResult.fail("请求异常: " + e.getMessage());
        }
        return parseResponse(response);
    }

    /// 构造飞书 interactive 卡片 body
    private String buildCardBody(String title, String message) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", Map.of("wide_screen_mode", true));
        card.put("header", Map.of(
                "title", Map.of(
                        "tag", "plain_text",
                        "content", title == null ? "" : title
                )
        ));
        card.put("elements", List.of(
                Map.of(
                        "tag", "markdown",
                        "content", message == null ? "" : message
                )
        ));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "interactive");
        body.put("card", card);
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("飞书 body 序列化失败", e);
        }
    }

    String appendSignIfNeeded(String url, String secret) {
        if (StrUtil.isBlank(secret)) {
            return url;
        }
        // 飞书官方：timestamp 用秒，不用毫秒
        long ts = System.currentTimeMillis() / 1000;
        String stringToSign = ts + "\n" + secret;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            // 飞书官方：Base64 编码后直接拼接，不做 URLEncoder（与钉钉不同）
            String sign = Base64.getEncoder().encodeToString(signData);
            String separator = url.contains("?") ? "&" : "?";
            return url + separator + "timestamp=" + ts + "&sign=" + sign;
        }
        catch (Exception e) {
            throw new RuntimeException("飞书签名计算失败", e);
        }
    }

    TestWebhookResult parseResponse(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            return TestWebhookResult.fail("响应为空");
        }
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode codeNode = root.get("code");
            if (codeNode == null || codeNode.isNull()) {
                return TestWebhookResult.fail("无法识别的响应格式");
            }
            int code = codeNode.asInt();
            String msg = root.path("msg").asString("");
            if (code == 0) {
                return TestWebhookResult.ok(StrUtil.isNotBlank(msg) ? msg : "ok");
            }
            return TestWebhookResult.fail(StrUtil.isNotBlank(msg) ? msg : ("code=" + code));
        }
        catch (Exception e) {
            return TestWebhookResult.fail("响应格式异常，无法解析");
        }
    }
}