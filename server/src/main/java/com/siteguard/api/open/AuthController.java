package com.siteguard.api.open;

import com.siteguard.common.dto.StatusResult;
import com.siteguard.common.exception.Errors;
import com.siteguard.auth.dto.AuthUserWithToken;
import com.siteguard.auth.dto.RefreshRequest;
import com.siteguard.auth.dto.UsernamePasswordAuthParams;
import com.siteguard.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/v1/open")
public class AuthController {
    private static final String REFRESH_TOKEN_COOKIE_KEY = "refreshToken";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录
     */
    @PostMapping("/auth/login")
    @Operation(summary = "登录", description = "使用账号密码登录")
    public AuthUserWithToken login(@RequestBody UsernamePasswordAuthParams request, HttpServletResponse response) {
        var user = authService.signin(request);
        var refreshCookie = new Cookie(REFRESH_TOKEN_COOKIE_KEY,
                user.getRefreshToken());
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(30 * 24 * 60 * 60); // 30 天
        refreshCookie.setAttribute("SameSite", "Lax");
        response.addCookie(refreshCookie);
        return user;
    }

    /**
     * 刷新 token
     */
    @PostMapping("/auth/refresh")
    public AuthUserWithToken refresh(@RequestBody(required = false) RefreshRequest params,
                                     HttpServletRequest request) {
        var token = extractToken(params, request);
        return authService.refresh(token);
    }

    /**
     * 登出
     */
    @PostMapping("/auth/logout")
    public StatusResult<Void> logout(HttpServletResponse response) {
        // 清除 Refresh Token Cookie
        Cookie refreshCookie = new Cookie(REFRESH_TOKEN_COOKIE_KEY, "");
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(0); // 立即过期
        response.addCookie(refreshCookie);

        return StatusResult.ok();
    }

    /**
     * 从 Cookie 中提取 refresh token
     */
    private Optional<String> extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (REFRESH_TOKEN_COOKIE_KEY.equals(cookie.getName())) {
                    return Optional.of(cookie.getValue());
                }
            }
        }
        return Optional.empty();
    }

    /// 按"请求体 > Cookie"优先级拿 refresh token;两者都拿不到时抛 401,
    /// 避免历史上 params==null 时直接 NPE 把整条请求打成 500 的问题。
    private String extractToken(RefreshRequest params, HttpServletRequest request) {
        if (params != null && params.getToken() != null && !params.getToken().isBlank()) {
            return params.getToken();
        }
        return extractTokenFromCookie(request)
                .filter(t -> !t.isBlank())
                .orElseThrow(() -> Errors.UNAUTHORIZED.toException("刷新令牌缺失"));
    }
}

