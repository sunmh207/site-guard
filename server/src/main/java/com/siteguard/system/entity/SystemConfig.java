package com.siteguard.system.entity;

import com.siteguard.common.persistent.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/// 系统配置 KV 实体。
///
/// 每个 configKey 全局唯一；configValue 存 JSON 序列化字符串。
/// 由 [com.siteguard.system.service.ConfigService] 统一读写。
@Entity
@Table(name = "system_config", uniqueConstraints = {
    @UniqueConstraint(name = "uk_system_config_key", columnNames = "config_key")
})
@Getter @Setter @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
public class SystemConfig extends BaseEntity {

    @Column(name = "config_key", nullable = false, length = 64)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;
}