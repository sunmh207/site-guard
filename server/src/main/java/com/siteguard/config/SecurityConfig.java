package com.siteguard.config;

import com.siteguard.common.exception.ErrorResponse;
import com.siteguard.auth.filter.JwtAuthenticationFilter;
import com.siteguard.auth.service.AuthService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())  // 禁用 CSRF（因为使用 JWT）
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // 无状态会话
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(),
                                    ErrorResponse.of(401, "UNAUTHORIZED", "未登录或登录已过期"));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(),
                                    ErrorResponse.of(403, "ACCESS_DENIED", "无权访问"));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/open/**",
                                "/v3/api-docs/**",
                                // Swagger UI 路径：Spring Boot 默认入口为 /swagger-ui.html（重定向到 /swagger-ui/index.html）
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                // Actuator 健康检查放行：供 Docker / Kubernetes 探活使用
                                "/actuator/**",
                                "/"
                        ).permitAll() //路径允许所有人访问
                        .anyRequest().authenticated()  // 其他请求需要认证
                )
                .addFilterBefore(new JwtAuthenticationFilter(authService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
