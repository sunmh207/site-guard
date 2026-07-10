package com.siteguard.system.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/// 通知配置合并工具。
///
/// 处理「签名密钥编辑留空 → 保持原值」的语义：
///   - new.secret 为 null 或空字符串 → 沿用 existing.secret
///   - new.secret 非空 → 使用 new.secret 覆盖
///
/// 其他字段一律以 new 为准。
///
/// 注册为 Spring 组件供 ConfigServiceImpl 注入（D5 全栈测试发现缺 @Component
/// 会导致 SpringBoot 测试上下文启动失败）。
@Component
public class NotificationConfigMerger {

    public NotificationConfig merge(NotificationConfig newCfg, NotificationConfig existing) {
        if (newCfg == null) return existing;
        if (StringUtils.isBlank(newCfg.getSecret())) {
            newCfg.setSecret(existing == null ? null : existing.getSecret());
        }
        return newCfg;
    }
}