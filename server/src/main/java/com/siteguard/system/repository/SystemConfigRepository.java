package com.siteguard.system.repository;

import com.siteguard.system.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/// 系统配置数据访问层
///
/// 通过 [com.siteguard.system.service.ConfigService] 调用，
/// 上层不直接使用本接口。
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    Optional<SystemConfig> findByConfigKey(String configKey);

    void deleteByConfigKey(String configKey);
}