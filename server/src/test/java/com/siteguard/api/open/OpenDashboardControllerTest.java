package com.siteguard.api.open;

import com.siteguard.monitor.dto.DashboardResponse;
import com.siteguard.monitor.dto.DashboardSummaryDTO;
import com.siteguard.monitor.service.SiteCheckService;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// 公开 dashboard 端点的 MockMvc 集成测试：
/// - 与 AdminSiteControllerTest 风格一致（@SpringBootTest + @MockitoBean 替换 SiteCheckService）
/// - SecurityConfig 已对 /api/v1/open/** 放行；这里 addFilters = false 跳过安全过滤器
/// - 关闭 Flyway 校验的原因与 AiCoursewareApplicationTests 一致
/// - 开关由 ConfigService 给出，未真正访问数据库
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "spring.flyway.validate-on-migrate=false")
class OpenDashboardControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SiteCheckService siteCheckService;

    @MockitoBean
    ConfigService configService;

    @Test
    void dashboard_returnsOk_withSummaryAndRecentAlerts() throws Exception {
        when(configService.getOrDefault(ConfigKey.OPEN_DASHBOARD, false)).thenReturn(true);
        // 构造 total=10, healthy=6, abnormal=2, pending=1, paused=1
        var summary = new DashboardSummaryDTO(10L, 6L, 2L, 1L, 1L, 123.0);
        var response = new DashboardResponse(summary, List.of());
        when(siteCheckService.getDashboard()).thenReturn(response);

        mvc.perform(get("/api/v1/open/site/stats/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalSites").value(10))
                .andExpect(jsonPath("$.summary.healthyCount").value(6))
                .andExpect(jsonPath("$.summary.abnormalCount").value(2))
                .andExpect(jsonPath("$.summary.pendingCount").value(1))
                .andExpect(jsonPath("$.summary.pausedCount").value(1))
                .andExpect(jsonPath("$.summary.avgResponseMs").value(123.0))
                .andExpect(jsonPath("$.recentAlerts").isArray());
    }

    /// 关闭分支：默认走 NOT_FOUND（前端拿到 404 切换到友好提示页）。
    /// 关键不变量：开关关闭时聚合服务不能被调——避免一次意外的「读一次数据库」。
    @Test
    void dashboard_disabled_returns404_andDoesNotCallService() throws Exception {
        when(configService.getOrDefault(ConfigKey.OPEN_DASHBOARD, false)).thenReturn(false);

        mvc.perform(get("/api/v1/open/site/stats/dashboard"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(siteCheckService, never()).getDashboard();
    }

    /// 兜底：DB 没这行配置时（key 不存在）也走关闭分支。
    /// 防御「隐式默认启用」风险：从未配过 = 永远不放出数据。
    @Test
    void dashboard_keyAbsent_returns404() throws Exception {
        /// getOrDefault 的 fallback 就是 false；不存在的情况下 repo 不返回 entity
        when(configService.getOrDefault(ConfigKey.OPEN_DASHBOARD, false)).thenReturn(false);

        mvc.perform(get("/api/v1/open/site/stats/dashboard"))
                .andExpect(status().isNotFound());

        verify(siteCheckService, never()).getDashboard();
    }
}