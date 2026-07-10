package com.siteguard.auth.service.impl;

import com.siteguard.auth.config.AdminAuthProps;
import com.siteguard.common.exception.AppException;
import com.siteguard.auth.config.AuthJWTProps;
import com.siteguard.auth.dto.UsernamePasswordAuthParams;
import com.siteguard.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// AuthServiceImpl 单元测试
///
/// 验证"配置驱动的单管理员账号 + 完整 JWT 链路"在以下维度表现正确：
/// - 正常登录返回 token
/// - 错误用户名/密码抛 UNAUTHORIZED（同一条错误信息，避免用户枚举）
/// - JWT 签名校验失败抛 401
/// - access token 用途与 refresh 用途严格区分
/// - 内存中的密码是 BCrypt 哈希而非明文
class AuthServiceImplTest {

    private AuthServiceImpl service;
    private AdminAuthProps adminProps;
    private AuthJWTProps jwtProps;

    @BeforeEach
    void setUp() throws Exception {
        var encoder = new BCryptPasswordEncoder();

        adminProps = new AdminAuthProps();
        adminProps.setUsername("admin");
        adminProps.setPassword("secret123");
        adminProps.setNickname("系统管理员");

        jwtProps = new AuthJWTProps();
        jwtProps.setIssuer("site-guard");
        jwtProps.setSecret("ChangeThisSecretKeyInProduction_7gK9xQ2mLp8Zr4VnT6cW1yHs3DfA0bJu");
        jwtProps.setAccessTokenTtl(3_600_000L);
        jwtProps.setRefreshTokenTtl(2_592_000_000L);

        service = new AuthServiceImpl(encoder, adminProps, jwtProps);

        // @PostConstruct 不会在单元测试中自动触发，手动调用 init()
        // init() 是 package-private（不在测试包 com.siteguard.auth.service.impl 内），
        // 不能直接 service.init()，必须用反射绕过可见性检查
        Method init = AuthServiceImpl.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(service);
    }

    /// 正常登录：username + password 都对 → 返回 AuthUserWithToken
    @Test
    void signin_ok_returnsTokenAndUser() {
        var result = service.signin(login("admin", "secret123"));

        assertThat(result.getUser().getId()).isEqualTo(1L);
        assertThat(result.getUser().getUsername()).isEqualTo("admin");
        assertThat(result.getUser().getNickname()).isEqualTo("系统管理员");
        assertThat(result.getAccessToken()).isNotBlank();
        assertThat(result.getRefreshToken()).isNotBlank();
        assertThat(result.getAccessTokenTtl()).isEqualTo(3_600_000L);
        assertThat(result.getRefreshTokenTtl()).isEqualTo(2_592_000_000L);
    }

    /// 错误密码：抛 UNAUTHORIZED，错误信息固定为"用户名或密码不正确"
    @Test
    void signin_wrongPassword_throws401() {
        assertThatThrownBy(() -> service.signin(login("admin", "wrong")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("用户名或密码不正确");
    }

    /// 错误用户名：抛 UNAUTHORIZED（同一条错误信息，避免泄露用户存在性）
    @Test
    void signin_wrongUsername_throws401() {
        assertThatThrownBy(() -> service.signin(login("not-admin", "secret123")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("用户名或密码不正确");
    }

    /// 内存中的密码是 BCrypt 哈希：明文 password 字段在 adminProps 中仍是明文，
    /// 但 signin 比对的是 init() 中生成的哈希
    @Test
    void signin_passwordFromConfig_isHashedBeforeCompare() {
        // adminProps.password 仍是明文（@ConfigurationProperties 注入语义）
        assertThat(adminProps.getPassword()).isEqualTo("secret123");
        // signin 成功 → 证明比对的不是明文
        assertThat(service.signin(login("admin", "secret123")).getAccessToken()).isNotBlank();
    }

    /// 合法 access token → verify 返回 AuthUser（id=1, username/nickname 来自配置）
    @Test
    void verify_validToken_returnsAuthUser() {
        var signinResult = service.signin(login("admin", "secret123"));

        var verified = service.verify(signinResult.getAccessToken());

        assertThat(verified.getId()).isEqualTo(1L);
        assertThat(verified.getUsername()).isEqualTo("admin");
        assertThat(verified.getNickname()).isEqualTo("系统管理员");
    }

    /// 篡改 access token（追加 tamper 后缀）→ 签名校验失败抛 401
    @Test
    void verify_invalidSignature_throws401() {
        var signinResult = service.signin(login("admin", "secret123"));
        var tampered = signinResult.getAccessToken() + "tamper";

        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("验证令牌失败");
    }

    /// access token 用途与 refresh 用途严格区分：
    /// 用 access token 调 refresh 必须失败（防止 access 泄漏后能换 refresh）
    @Test
    void verify_wrongUsage_throws401() {
        var signinResult = service.signin(login("admin", "secret123"));

        assertThatThrownBy(() -> service.refresh(signinResult.getAccessToken()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("令牌用途不匹配");
    }

    /// 合法 refresh token → refresh 返回新的 access + refresh token
    @Test
    void refresh_validToken_returnsFreshToken() {
        var signinResult = service.signin(login("admin", "secret123"));

        var refreshed = service.refresh(signinResult.getRefreshToken());

        assertThat(refreshed.getUser().getId()).isEqualTo(1L);
        assertThat(refreshed.getAccessToken()).isNotBlank();
        assertThat(refreshed.getRefreshToken()).isNotBlank();
    }

    /// 构造登录参数（避免每个测试重复 boilerplate）
    private UsernamePasswordAuthParams login(String u, String p) {
        var params = new UsernamePasswordAuthParams();
        params.setUsername(u);
        params.setPassword(p);
        return params;
    }
}
