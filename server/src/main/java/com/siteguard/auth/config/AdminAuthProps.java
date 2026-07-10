package com.siteguard.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/// 管理员账号配置
///
/// 从 application.yaml 的 `app.auth.admin` 段读取明文账号与密码。
/// 启动时由 AuthServiceImpl 调用 PasswordEncoder 一次性 BCrypt 哈希入内存，
/// 运行时不再查询数据库，也不再写库。
@Component
@ConfigurationProperties(prefix = "app.auth.admin")
@Data
public class AdminAuthProps {
    /// 管理员用户名（登录用，与请求体里的 username 比对）
    private String username;

    /// 管理员密码（明文，启动时哈希入内存；改密码改 application.yaml 后重启服务）
    private String password;

    /// 管理员昵称（返回给前端 useState('auth:user') 中的 nickname 字段）
    private String nickname;
}
