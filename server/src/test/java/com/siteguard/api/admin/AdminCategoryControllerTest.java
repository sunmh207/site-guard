package com.siteguard.api.admin;

import com.siteguard.category.dto.CategoryCreateParams;
import com.siteguard.category.dto.CategoryTreeNode;
import com.siteguard.category.service.CategoryService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/// AdminCategoryController 的 MockMvc 集成测试：
/// - 用真实 MySQL 拉起 Spring 上下文，覆盖整条 Spring MVC 链路（路由 / 参数解析 / JSON 序列化）
/// - 通过 @MockitoBean 替换 CategoryService，避免真打 DB（Spring Boot 4 已用
///   @MockitoBean 取代旧的 @MockBean）
/// - 关闭 Flyway 校验与 SpringSecurity 过滤器的原因与 AdminSiteControllerTest 一致：
///   DB 残留一行历史孤儿迁移（version=20260630112000 QuartzTables），原始 SQL 文件
///   已不可重建。
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "spring.flyway.validate-on-migrate=false")
class AdminCategoryControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @MockitoBean
    CategoryService service;

    @Test
    void tree_returnsList() throws Exception {
        var n = new CategoryTreeNode();
        n.setId(1L); n.setName("默认分类"); n.setSystemFlag(true); n.setSiteCount(0L);
        n.setChildren(List.of());
        when(service.tree()).thenReturn(List.of(n));

        mvc.perform(get("/api/v1/admin/category/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("默认分类"));
    }

    @Test
    void create_returns201() throws Exception {
        var n = new CategoryTreeNode();
        n.setId(2L); n.setName("浙江"); n.setParentId(1L); n.setSiteCount(0L);
        n.setChildren(List.of());
        when(service.create(any())).thenReturn(n);

        var body = om.writeValueAsString(new CategoryCreateParams() {{
            setParentId(1L); setName("浙江");
        }});

        mvc.perform(post("/api/v1/admin/category/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("浙江"));
    }

    @Test
    void delete_returnsOk() throws Exception {
        doNothing().when(service).delete(1L, 99L);

        mvc.perform(post("/api/v1/admin/category/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"fallbackId\":99}"))
                .andExpect(status().isOk());
    }
}