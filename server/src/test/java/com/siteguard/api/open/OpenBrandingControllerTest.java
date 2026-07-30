package com.siteguard.api.open;

import com.siteguard.branding.dto.BrandingResponse;
import com.siteguard.branding.service.BrandingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:open-branding;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.quartz.job-store-type=memory"
})
class OpenBrandingControllerTest {

    private static final String VERSION = "a".repeat(64);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BrandingService brandingService;

    @Test
    void get_returnsPublicBrandingMetadata() throws Exception {
        when(brandingService.get()).thenReturn(BrandingResponse.builder()
                .name("My Site").iconUrl("/favicon.ico").customIcon(false).build());

        mvc.perform(get("/api/v1/open/branding/get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("Ok"))
                .andExpect(jsonPath("$.data.name").value("My Site"));
    }

    @Test
    void getIcon_returnsPngCacheEtagAndNosniffHeaders() throws Exception {
        when(brandingService.getIcon(VERSION)).thenReturn(new byte[]{1, 2, 3});

        mvc.perform(get("/api/v1/open/branding/icon/get").param("version", VERSION))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("ETag", '"' + VERSION + '"'))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void getIcon_matchingEtag_returns304AfterAuthorizingCurrentVersion() throws Exception {
        when(brandingService.getIcon(VERSION)).thenReturn(new byte[]{1, 2, 3});

        mvc.perform(get("/api/v1/open/branding/icon/get")
                        .param("version", VERSION)
                        .header("If-None-Match", '"' + VERSION + '"'))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", '"' + VERSION + '"'));

        /// 即使命中 ETag 也必须先让服务确认该版本仍是当前配置，防止旧图标被长期缓存继续访问。
        verify(brandingService).getIcon(VERSION);
    }
}
