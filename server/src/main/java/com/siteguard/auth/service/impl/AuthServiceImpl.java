package com.siteguard.auth.service.impl;

import com.siteguard.auth.config.AdminAuthProps;
import com.siteguard.common.exception.Errors;
import com.siteguard.auth.config.AuthJWTProps;
import com.siteguard.auth.dto.AuthUser;
import com.siteguard.auth.dto.AuthUserWithToken;
import com.siteguard.auth.dto.UsernamePasswordAuthParams;
import com.siteguard.auth.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Objects;

/// 认证服务
///
/// 单管理员账号：账号密码来自 application.yaml 的 `app.auth.admin` 段，
/// 启动时由 Spring 调用 {@link #init()} 一次性 BCrypt 哈希入内存，
/// 运行时不再查询数据库，也不再写库。改密码改 application.yaml 后重启服务生效。
@Service
public class AuthServiceImpl implements AuthService {
    private static final String USAGE_REFRESH = "refresh";
    private static final String USAGE_ACCESS = "access";

    /// JWT 中 subject 固定为字符串 "admin"（不再被解析为用户 ID 查库）
    private static final String JWT_SUBJECT = "admin";

    /// 登录响应中 AuthUser.id 的固定常量（项目内无多用户/角色概念）
    private static final long AUTH_USER_ID = 1L;

    private final PasswordEncoder encoder;
    private final AdminAuthProps adminProps;
    private final AuthJWTProps jwtProps;

    /// 启动时由 {@link #init()} 一次性 BCrypt 哈希后的密码缓存
    /// 运行期不变——不支持配置文件热更新
    private String cachedPasswordHash;

    public AuthServiceImpl(PasswordEncoder encoder, AdminAuthProps adminProps, AuthJWTProps jwtProps) {
        this.encoder = encoder;
        this.adminProps = adminProps;
        this.jwtProps = jwtProps;
    }

    /// 启动后立即把 application.yaml 中的明文密码哈希入内存
    ///
    /// 若未配置 `app.auth.admin.password` 则启动失败，避免运行期登录时才发现
    @PostConstruct
    void init() {
        if (adminProps.getPassword() == null || adminProps.getPassword().isBlank()) {
            throw new IllegalStateException("app.auth.admin.password 未配置");
        }
        this.cachedPasswordHash = encoder.encode(adminProps.getPassword());
    }

    /// 登录：username 严格相等 + BCrypt 匹配内存中的密码哈希
    @Override
    public AuthUserWithToken signin(UsernamePasswordAuthParams params) {
        if (!Objects.equals(params.getUsername(), adminProps.getUsername())
                || !encoder.matches(params.getPassword(), cachedPasswordHash)) {
            throw Errors.UNAUTHORIZED.toException("登录失败：用户名或密码不正确");
        }
        return buildFullAuthUser();
    }

    /// 刷新：只校验 refresh token 签名/issuer/usage 合法后发新 token
    /// 不再校验"用户仍存在"——单管理员场景下 token 即证明权限
    @Override
    public AuthUserWithToken refresh(String refreshToken) {
        decodeToken(refreshToken, USAGE_REFRESH);
        return buildFullAuthUser();
    }

    /// 鉴权：校验 access token 合法后返回 AuthUser
    /// 返回的 user 字段（id/username/nickname）来自 application.yaml 配置
    @Override
    public AuthUser verify(String token) {
        decodeToken(token, USAGE_ACCESS);
        return buildAuthUser();
    }

    private AuthUser buildAuthUser() {
        var au = new AuthUser();
        au.setId(AUTH_USER_ID);
        au.setUsername(adminProps.getUsername());
        au.setNickname(adminProps.getNickname());
        return au;
    }

    private AuthUserWithToken buildFullAuthUser() {
        var authUser = buildAuthUser();
        var result = new AuthUserWithToken();
        result.setUser(authUser);
        result.setAccessToken(generateAccessToken());
        result.setAccessTokenTtl(jwtProps.getAccessTokenTtl());
        result.setRefreshToken(generateRefreshToken());
        result.setRefreshTokenTtl(jwtProps.getRefreshTokenTtl());
        return result;
    }

    private String generateAccessToken() {
        return generateToken(USAGE_ACCESS, jwtProps.getAccessTokenTtl());
    }

    private String generateRefreshToken() {
        return generateToken(USAGE_REFRESH, jwtProps.getRefreshTokenTtl());
    }

    /// 抽取 JWT 生成逻辑：access 与 refresh 共用，仅 TTL 与 usage 不同
    private String generateToken(String usage, Long ttlMillis) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProps.getSecret().getBytes());
        return Jwts.builder()
                .subject(JWT_SUBJECT)
                .issuer(jwtProps.getIssuer())
                .expiration(new Date(System.currentTimeMillis() + ttlMillis))
                .claim("for", usage)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /// 校验 JWT 签名、issuer、usage；不再解析 subject 查库
    private void decodeToken(String token, String usage) {
        Claims claims;
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtProps.getSecret().getBytes());
            claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(jwtProps.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw Errors.UNAUTHORIZED.toException("验证令牌失败：{}", e.getMessage());
        }

        String tokenUsage = claims.get("for", String.class);
        if (!usage.equalsIgnoreCase(tokenUsage)) {
            throw Errors.UNAUTHORIZED.toException("验证令牌失败：令牌用途不匹配 (expected: {})", usage);
        }
    }
}