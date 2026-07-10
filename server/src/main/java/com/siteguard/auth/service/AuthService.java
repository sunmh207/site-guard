package com.siteguard.auth.service;


import com.siteguard.auth.dto.AuthUser;
import com.siteguard.auth.dto.AuthUserWithToken;
import com.siteguard.auth.dto.UsernamePasswordAuthParams;

/// 认证服务接口 提供用户认证相关的核心功能，包括令牌生成、刷新和验证。
public interface AuthService {

    /// 使用用户名密码生成认证用户
    ///
    /// @param params 用户名密码认证参数
    /// @return 完整的认证用户对象
    AuthUserWithToken signin(UsernamePasswordAuthParams params);


    /// 刷新认证
    ///
    /// @param refreshToken 刷新 Token
    /// @return 完整的认证用户对象
    AuthUserWithToken refresh(String refreshToken);

    /// 校验认证
    ///
    /// @param token AccessToken
    /// @return 认证用户对象
    AuthUser verify(String token);
}
