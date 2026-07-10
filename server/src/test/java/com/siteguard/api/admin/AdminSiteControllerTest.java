package com.siteguard.api.admin;

import com.siteguard.monitor.dto.DashboardResponse;
import com.siteguard.monitor.dto.DashboardSummaryDTO;
import com.siteguard.monitor.service.SiteCheckService;
import com.siteguard.site.dto.SiteDTO;
import com.siteguard.site.entity.SiteStatus;
import com.siteguard.site.service.SiteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// AdminSiteController 的 MockMvc 集成测试：
/// - 用真实 MySQL 拉起 Spring 上下文，覆盖整条 Spring MVC 链路（路由 / 参数解析 / JSON 序列化）
/// - 通过 @MockitoBean 替换 SiteService / SiteCheckService，避免真打 DB
/// - 关闭 Flyway 校验的原因与 AiCoursewareApplicationTests 一致：DB 残留
///   一行历史孤儿迁移（version=20260630112000 QuartzTables），原始 SQL 文件
///   已不可重建，详见 AiCoursewareApplicationTests 上的注释。
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "spring.flyway.validate-on-migrate=false")
class AdminSiteControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SiteService siteService;

    @MockitoBean
    SiteCheckService siteCheckService;

    @Test
    void search_returnsPager() throws Exception {
        var dto = new SiteDTO();
        dto.setId(1L);
        dto.setName("官网");
        dto.setUrl("https://example.com");
        dto.setAvailabilityStatus(SiteStatus.UNKNOWN);
        var pageRequest = PageRequest.of(0, 20);
        var page = new PageImpl<>(List.of(dto), pageRequest, 1L);
        when(siteService.search(any(), any())).thenReturn(page);

        mvc.perform(get("/api/v1/admin/site/search").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].name").value("官网"));
    }

    @Test
    void get_returnsDetail() throws Exception {
        var dto = new SiteDTO();
        dto.setId(1L);
        dto.setName("官网");
        when(siteService.getDetail(1L)).thenReturn(dto);

        mvc.perform(get("/api/v1/admin/site/get").param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create_invalidUrl_returns400() throws Exception {
        var body = "{\"name\":\"官网\",\"url\":\"not-a-url\"}";

        mvc.perform(post("/api/v1/admin/site/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returnsOk() throws Exception {
        var dto = new SiteDTO();
        dto.setId(1L);
        dto.setName("官网");
        when(siteService.update(any())).thenReturn(dto);

        var body = "{\"id\":1,\"name\":\"官网\",\"url\":\"https://example.com\"}";

        mvc.perform(post("/api/v1/admin/site/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void delete_returnsOk() throws Exception {
        var body = "{\"id\":1}";

        mvc.perform(post("/api/v1/admin/site/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void dashboard_returnsOk() throws Exception {
        // 构造 total=10, healthy=7, abnormal=2, pending=0, paused=1 的卡片场景
        var summary = new DashboardSummaryDTO(10L, 7L, 2L, 0L, 1L, 123.0);
        var response = new DashboardResponse(summary, List.of());
        when(siteCheckService.getDashboard()).thenReturn(response);

        mvc.perform(get("/api/v1/admin/site/stats/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalSites").value(10))
                .andExpect(jsonPath("$.summary.healthyCount").value(7))
                .andExpect(jsonPath("$.summary.abnormalCount").value(2))
                .andExpect(jsonPath("$.summary.pendingCount").value(0))
                .andExpect(jsonPath("$.summary.pausedCount").value(1))
                .andExpect(jsonPath("$.summary.avgResponseMs").value(123.0))
                .andExpect(jsonPath("$.recentAlerts").isArray());
    }
}
