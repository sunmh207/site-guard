package com.siteguard.system.service.impl;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.siteguard.common.exception.Errors;
import com.siteguard.system.config.NotificationConfig;
import com.siteguard.system.config.NotificationConfigMerger;
import com.siteguard.system.entity.SystemConfig;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.repository.SystemConfigRepository;
import com.siteguard.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/// 系统配置服务实现。
///
/// - get/set/delete 走 SystemConfigRepository
/// - JSON 序列化使用 Jackson ObjectMapper
/// - 写入时按 key 分发合并策略：NotificationConfig 走 NotificationConfigMerger（处理 secret 留空保留语义），
///   其他配置直接序列化。
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigServiceImpl implements ConfigService {

    private final SystemConfigRepository repo;
    private final ObjectMapper objectMapper;
    private final NotificationConfigMerger notificationMerger;

    @Override
    public JsonNode getNode(ConfigKey key) {
        var entity = repo.findByConfigKey(key.getKey())
            .orElseThrow(() -> Errors.NOT_FOUND.toException("配置 {} 不存在", key.getKey()));
        try {
            return objectMapper.readTree(entity.getConfigValue());
        } catch (JacksonException e) {
            throw Errors.INVALID_ARGUMENT.toException(e, "配置 {} 反序列化失败: {}", key.getKey(), e.getMessage());
        }
    }

    @Override
    public <T> T get(ConfigKey key) {
        var node = getNode(key);
        try {
            /// ConfigKey.valueType 在枚举声明处已绑定具体 T（如 NotificationConfig.class），
            /// 此处 unchecked cast 是安全的；ObjectMapper 实际反序列化类型由 key 决定。
            @SuppressWarnings("unchecked")
            Class<T> type = (Class<T>) key.getValueType();
            return objectMapper.treeToValue(node, type);
        } catch (JacksonException e) {
            throw Errors.INVALID_ARGUMENT.toException(e, "配置 {} 反序列化失败: {}", key.getKey(), e.getMessage());
        }
    }

    @Override
    public <T> T getOrDefault(ConfigKey key, T fallback) {
        return repo.findByConfigKey(key.getKey())
            .<T>map(entity -> {
                try {
                    /// ConfigKey.valueType 在枚举声明处已绑定具体 T（如 NotificationConfig.class），
                    /// 此处 unchecked cast 是安全的；ObjectMapper 实际反序列化类型由 key 决定。
                    @SuppressWarnings("unchecked")
                    Class<T> type = (Class<T>) key.getValueType();
                    return objectMapper.readValue(entity.getConfigValue(), type);
                } catch (JacksonException e) {
                    throw Errors.INVALID_ARGUMENT.toException(e, "配置 {} 反序列化失败: {}", key.getKey(), e.getMessage());
                }
            })
            .orElse(fallback);
    }

    @Override
    public <T> void set(ConfigKey key, T value) {
        // NotificationConfig 走专属合并（secret 留空保留）
        if (key == ConfigKey.NOTIFICATION && value instanceof NotificationConfig newCfg) {
            var existing = repo.findByConfigKey(key.getKey())
                .map(e -> {
                    try {
                        return objectMapper.readValue(e.getConfigValue(), NotificationConfig.class);
                    } catch (JacksonException ex) {
                        throw Errors.INVALID_ARGUMENT.toException(ex, "配置 {} 反序列化失败: {}", key.getKey(), ex.getMessage());
                    }
                })
                .orElse(null);
            var merged = notificationMerger.merge(newCfg, existing);
            writeValue(key, merged);
            return;
        }
        writeValue(key, value);
    }

    @Override
    public void delete(ConfigKey key) {
        var entity = repo.findByConfigKey(key.getKey())
            .orElseThrow(() -> Errors.NOT_FOUND.toException("配置 {} 不存在", key.getKey()));
        repo.delete(entity);
        log.info("删除系统配置成功，key: {}", key.getKey());
    }

    private void writeValue(ConfigKey key, Object value) {
        String json;
        try {
            json = objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw Errors.INVALID_ARGUMENT.toException(e, "配置 {} 序列化失败: {}", key.getKey(), e.getMessage());
        }
        var now = Instant.now().toEpochMilli();
        var entityOpt = repo.findByConfigKey(key.getKey());
        if (entityOpt.isPresent()) {
            var entity = entityOpt.get();
            entity.setConfigValue(json);
            entity.setUpdatedAt(now);
            repo.save(entity);
            log.info("更新系统配置成功，key: {}", key.getKey());
        } else {
            var entity = new SystemConfig();
            entity.setConfigKey(key.getKey());
            entity.setConfigValue(json);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            repo.save(entity);
            log.info("创建系统配置成功，key: {}", key.getKey());
        }
    }
}
