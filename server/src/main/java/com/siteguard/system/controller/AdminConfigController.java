package com.siteguard.system.controller;

import tools.jackson.databind.ObjectMapper;
import com.siteguard.common.dto.StatusResult;
import com.siteguard.notify.dto.TestWebhookParams;
import com.siteguard.notify.dto.TestWebhookResult;
import com.siteguard.notify.service.NotifyService;
import com.siteguard.system.dto.ConfigDeleteParams;
import com.siteguard.system.dto.ConfigResponse;
import com.siteguard.system.dto.ConfigUpdateParams;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// 系统配置管理后台接口。
///
/// 路径：/api/v1/admin/config
/// 设计风格：仅 GET 与 POST，按动作命名（get / set / delete / test-webhook）。
/// 与 AdminSiteController / AdminUserController 保持一致。
@RestController
@RequestMapping("/api/v1/admin/config")
@RequiredArgsConstructor
@Tag(name = "系统配置", description = "后台 KV 配置管理")
public class AdminConfigController {

    private final ConfigService configService;
    private final NotifyService notifyService;
    private final ObjectMapper objectMapper;

    /// 读取配置
    @Operation(summary = "读取配置")
    @GetMapping("/get")
    public StatusResult<ConfigResponse> get(
            @RequestParam @Parameter(description = "配置键", required = true) String key) {
        var configKey = ConfigKey.fromString(key);
        var node = configService.getNode(configKey);
        var entity = new ConfigResponse();
        entity.setKey(key);
        entity.setValue(node);
        return StatusResult.success(entity);
    }

    /// 保存配置
    @Operation(summary = "保存配置")
    @PostMapping("/set")
    public StatusResult<ConfigResponse> set(@Valid @RequestBody ConfigUpdateParams params) {
        var configKey = ConfigKey.fromString(params.getKey());
        // 直接保存 JsonNode，由 ConfigService.set 按 key 类型做合并/校验
        configService.set(configKey, params.getValue());
        var resp = new ConfigResponse();
        resp.setKey(params.getKey());
        resp.setValue(params.getValue());
        return StatusResult.success(resp);
    }

    /// 删除配置
    @Operation(summary = "删除配置")
    @PostMapping("/delete")
    public StatusResult<Void> delete(@Valid @RequestBody ConfigDeleteParams params) {
        var configKey = ConfigKey.fromString(params.getKey());
        configService.delete(configKey);
        return StatusResult.ok();
    }

    /// 测试 Webhook 联通性（不依赖数据库）
    ///
    /// 当前 NotifyService.testWebhook 仍是 3-arg 签名（platform/url/secret），
    /// 拆包转发；Stage D 改造后会收敛为单参数 TestWebhookParams。
    @Operation(summary = "测试通知 Webhook 联通性")
    @PostMapping("/test-webhook")
    public StatusResult<TestWebhookResult> testWebhook(@Valid @RequestBody TestWebhookParams params) {
        return StatusResult.success(notifyService.testWebhook(
            params.getPlatform(), params.getWebhookUrl(), params.getSecret()));
    }
}