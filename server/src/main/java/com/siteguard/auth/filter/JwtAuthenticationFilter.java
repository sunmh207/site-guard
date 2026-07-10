package com.siteguard.auth.filter;

import com.siteguard.auth.dto.AuthUser;
import com.siteguard.auth.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * JWT 认证过滤器
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String COOKIE_KEY = "accessToken";

    private final AuthService authService;

    public JwtAuthenticationFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = extractJwtFromRequest(request);
            if (StringUtils.hasText(token)) {
                AuthUser authUser = authService.verify(token);
                if (authUser != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // 设置认证上下文
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    authUser,
                                    null,
                                    new ArrayList<>()
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            logger.error("JWT 认证失败: {}", e.getMessage());
            // 继续执行，让 Spring Security 处理未认证的请求
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中提取 JWT token
     * 优先从 Cookie 中获取，其次从 Header 中获取
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        // 先从 Cookie 中获取 accessToken
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (COOKIE_KEY.equals(cookie.getName())) {
                    logger.debug("从 HTTP Cookie 中获取到 token");
                    return cookie.getValue();
                }
            }
        }

        // 如果 Cookie 中没有，则从 Header 中获取
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            logger.debug("从 HTTP Header 中获取到 token");
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        return null;
    }
}
