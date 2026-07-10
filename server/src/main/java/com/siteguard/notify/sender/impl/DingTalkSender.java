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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/// 钉钉机器人发送器
///
/// - 加签：secret 非空时，按钉钉官方算法拼接 timestamp + sign 到 URL
///   sign = URLEncoder.encode(Base64(HmacSHA256("<ts>\n<secret>", secret)))
/// - 无 secret：直接 POST JSON body
/// - 消息类型：markdown（msgtype=markdown），text 字段支持完整 markdown 渲染
/// - 响应解析：errcode=0 表示成功
@Component
@Slf4j
public class DingTalkSender implements ImSender {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Override
    public RobotPlatform platform() {
        return RobotPlatform.DINGTALK;
    }

    @Override
    public void send(ImRobot robot, String title, String message) {
        String url = appendSignIfNeeded(robot.getWebhookUrl(), robot.getSecret());
        String body = buildMarkdownBody(title, message);
        String response = HttpUtil.post(url, body);
        log.debug("钉钉发送响应: {}", response);
        // 契约：发送失败抛 RuntimeException，由 NotifyService 捕获并向上抛出
        TestWebhookResult r = parseResponse(response);
        if (!Boolean.TRUE.equals(r.getSuccess())) {
            throw new RuntimeException("钉钉发送失败: " + r.getMessage());
        }
    }

    @Override
    public TestWebhookResult testWebhook(String webhookUrl, String secret) {
        String url = appendSignIfNeeded(webhookUrl, secret);
        String body = "{\"msgtype\":\"text\",\"text\":{\"content\":\"Webhook 连接测试成功\"}}";
        String response;
        try {
            response = HttpUtil.post(url, body);
        }
        catch (Exception e) {
            log.warn("钉钉 webhook 请求异常: {}", e.getMessage());
            return TestWebhookResult.fail("请求异常: " + e.getMessage());
        }
        return parseResponse(response);
    }

    /// 构造钉钉 markdown body：title 走钉钉卡片标题字段；message 直接透传，markdown 硬换行（`  \n`）由上游 NotificationListener.formatImText 注入
    private String buildMarkdownBody(String title, String message) {
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("title", title == null ? "" : title);
        markdown.put("text", message == null ? "" : message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");
        body.put("markdown", markdown);
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("钉钉 body 序列化失败", e);
        }
    }

    /// 加签 URL 拼接：secret 为空时直接返回原 URL
    String appendSignIfNeeded(String url, String secret) {
        if (StrUtil.isBlank(secret)) {
            return url;
        }
        long ts = System.currentTimeMillis();
        String stringToSign = ts + "\n" + secret;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
            // URL 已可能含 ?，统一用 & 拼接
            String separator = url.contains("?") ? "&" : "?";
            return url + separator + "timestamp=" + ts + "&sign=" + sign;
        }
        catch (Exception e) {
            throw new RuntimeException("钉钉签名计算失败", e);
        }
    }

    /// 钉钉响应：errcode 0 表示成功
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