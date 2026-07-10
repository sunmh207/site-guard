package com.siteguard.api.open;

import com.siteguard.auth.dto.AuthUserWithToken;
import com.siteguard.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// AuthController 的 MockMvc 集成测试。
/// 重点覆盖 /auth/refresh 在不同来源(body / cookie / 都没有)下取 token 的分支，
/// 避免历史 NPE(`params` 为 null 时直接 `.getToken()`)再次回归。
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "spring.flyway.validate-on-migrate=false")
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService authService;

    /// 空 body 且无 cookie：应返回 401 UNAUTHORIZED，不能再 NPE 打成 500。
    @Test
    void refresh_emptyBodyNoCookie_returns401() throws Exception {
        mvc.perform(post("/api/v1/open/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(authService, never()).refresh(anyString());
    }

    /// 空 body 但带 refreshToken cookie：应从 cookie 取 token 调用 service。
    @Test
    void refresh_emptyBodyWithCookie_usesCookieToken() throws Exception {
        var userWithToken = new AuthUserWithToken();
        when(authService.refresh("cookie-token")).thenReturn(userWithToken);

        mvc.perform(post("/api/v1/open/auth/refresh")
                        .cookie(new Cookie("refreshToken", "cookie-token")))
                .andExpect(status().isOk());

        verify(authService, times(1)).refresh("cookie-token");
    }

    /// body 显式带 token：以 body 为准。
    @Test
    void refresh_bodyToken_takesPrecedence() throws Exception {
        var userWithToken = new AuthUserWithToken();
        when(authService.refresh("body-token")).thenReturn(userWithToken);

        mvc.perform(post("/api/v1/open/auth/refresh")
                        .contentType("application/json")
                        .content("{\"token\":\"body-token\"}"))
                .andExpect(status().isOk());

        verify(authService, times(1)).refresh("body-token");
    }
}