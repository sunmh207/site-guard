package com.siteguard.api.admin;

import com.siteguard.branding.dto.BrandingResponse;
import com.siteguard.branding.service.BrandingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-branding;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.quartz.job-store-type=memory"
})
class AdminBrandingControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BrandingService brandingService;

    @Test
    void get_returnsStatusResultMetadata() throws Exception {
        when(brandingService.get()).thenReturn(response(true));

        mvc.perform(get("/api/v1/admin/branding/get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("Ok"))
                .andExpect(jsonPath("$.data.name").value("My Site"))
                .andExpect(jsonPath("$.data.customIcon").value(true));
    }

    @Test
    void set_acceptsMultipartNameAndIcon() throws Exception {
        var icon = new MockMultipartFile("icon", "icon.jpg", "image/jpeg", new byte[]{1, 2});
        when(brandingService.set(eq(" My Site "), any())).thenReturn(response(true));

        mvc.perform(multipart("/api/v1/admin/branding/set")
                        .file(icon)
                        .param("siteName", " My Site "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.iconUrl").value("/api/v1/open/branding/icon/get?version=" + "a".repeat(64)));

        verify(brandingService).set(eq(" My Site "), any());
    }

    @Test
    void deleteIcon_usesPostAndReturnsUpdatedBranding() throws Exception {
        when(brandingService.deleteIcon()).thenReturn(response(false));

        mvc.perform(post("/api/v1/admin/branding/icon/delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customIcon").value(false))
                .andExpect(jsonPath("$.data.iconUrl").value("/favicon.ico"));
    }

    private BrandingResponse response(boolean customIcon) {
        return BrandingResponse.builder()
                .name("My Site")
                .customIcon(customIcon)
                .iconUrl(customIcon
                        ? "/api/v1/open/branding/icon/get?version=" + "a".repeat(64)
                        : "/favicon.ico")
                .build();
    }
}
