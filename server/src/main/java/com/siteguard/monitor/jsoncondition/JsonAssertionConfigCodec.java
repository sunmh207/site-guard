package com.siteguard.monitor.jsoncondition;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// API 结构化 JSON 条件配置与数据库 TEXT 之间的唯一编解码边界。
@Component
@RequiredArgsConstructor
public class JsonAssertionConfigCodec {

    private final ObjectMapper objectMapper;
    private final JsonConditionEvaluator evaluator;

    public String encode(JsonAssertionConfigDTO config) {
        JsonAssertionConfigDTO normalized = evaluator.validateAndNormalize(config);
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON 条件配置序列化失败: " + e.getMessage(), e);
        }
    }

    public JsonAssertionConfigDTO decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            var config = objectMapper.readValue(encoded, JsonAssertionConfigDTO.class);
            return evaluator.validateAndNormalize(config);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new IllegalArgumentException("JSON 条件配置反序列化失败: " + e.getMessage(), e);
        }
    }
}
