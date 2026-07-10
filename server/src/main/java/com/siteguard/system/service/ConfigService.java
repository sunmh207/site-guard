package com.siteguard.system.service;

import tools.jackson.databind.JsonNode;
import com.siteguard.system.enums.ConfigKey;

/// 系统配置服务。
///
/// 通过 ConfigKey 枚举注册中心提供类型安全的 KV 读写。
/// 序列化由实现类用 Jackson ObjectMapper 完成。
public interface ConfigService {

    /// 读取 JSON 节点；不存在抛 NOT_FOUND。
    JsonNode getNode(ConfigKey key);

    /// 读取并反序列化为指定类型；不存在抛 NOT_FOUND。
    <T> T get(ConfigKey key);

    /// 读取并反序列化为指定类型；不存在返回 fallback（不抛异常）。
    <T> T getOrDefault(ConfigKey key, T fallback);

    /// 保存（不存在插入 / 存在覆盖）。
    /// NotificationConfig 等含特判合并逻辑的配置走对应 Merger；通用配置直接写入。
    <T> void set(ConfigKey key, T value);

    /// 删除；不存在抛 NOT_FOUND。
    void delete(ConfigKey key);
}
