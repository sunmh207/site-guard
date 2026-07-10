package com.siteguard.api.admin;

import tools.jackson.databind.ObjectMapper;
import com.siteguard.common.dto.IdPayload;
import com.siteguard.common.exception.Errors;
import com.siteguard.monitor.dto.SitePathRuleDTO;
import com.siteguard.monitor.dto.SitePathRuleListRequest;
import com.siteguard.monitor.service.SitePathRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.http.HttpClient;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// AdminSitePathRuleController 的 MockMvc 集成测试：
/// - 沿用 AdminSiteControllerTest 的风格：@SpringBootTest + @AutoConfigureMockMvc + @MockitoBean
/// - 关闭 Flyway 校验的原因与 AdminSiteControllerTest 一致（DB 残留一行历史孤儿迁移）
/// - 内嵌 TestConfig 提供一个 HttpClient bean：SiteGuardApplication 自身没有声明
///   HttpClient bean（生产里由调用方按需 newBuilder() 创建），但 SiteCheckServiceImpl ->
///   PathCheckProbe 链路需要注入一个；这里给测试上下文一个轻量实现，避免连锁失败。
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "spring.flyway.validate-on-migrate=false")
class AdminSitePathRuleControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        HttpClient httpClient() {
            return HttpClient.newHttpClient();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockitoBean SitePathRuleService service;

    @Test
    void list_returnsRules() throws Exception {
        when(service.listBySite(1L)).thenReturn(List.of(
                new SitePathRuleDTO(10L, 1L, "/app_dev.php", 200, 1_700_000_000_000L, 200, null, null)
        ));

        mvc.perform(get("/api/v1/admin/site/1/pathRules/get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].path").value("/app_dev.php"))
                .andExpect(jsonPath("$.data[0].expectedHttpStatus").value(200));
    }

    @Test
    void set_callsServiceWithParsedRequest() throws Exception {
        var req = new SitePathRuleListRequest(1L, List.of(
                new SitePathRuleDTO(null, 1L, "/a", 200, null, null, null, null)
        ));

        mvc.perform(post("/api/v1/admin/site/1/pathRules/set")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("Ok"));

        verify(service).set(any(SitePathRuleListRequest.class));
    }

    @Test
    void set_siteNotFound_returns404() throws Exception {
        // 与 SitePathRuleServiceImplTest#set_siteNotFound_throws 保持一致：
        // service 真正抛出的是 Errors.NOT_FOUND.toException(...)（AppException，status=404），
        // CustomExceptionHandler 会映射为 HTTP 404，而非兜底的 500。
        doThrow(Errors.NOT_FOUND.toException("站点不存在 (ID: {})", 99L)).when(service).set(any());
        // 非空列表让 @Valid 通过，让 doThrow 实际触发。
        var req = new SitePathRuleListRequest(99L, List.of(
                new SitePathRuleDTO(null, 99L, "/a", 200, null, null, null, null)
        ));

        mvc.perform(post("/api/v1/admin/site/99/pathRules/set")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteById_callsService() throws Exception {
        var payload = new IdPayload();
        payload.setId(10L);

        mvc.perform(post("/api/v1/admin/site/pathRule/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("Ok"));

        verify(service).delete(10L);
    }
}
