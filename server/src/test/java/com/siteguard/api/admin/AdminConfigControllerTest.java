package com.siteguard.api.admin;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.siteguard.common.dto.StatusResult;
import com.siteguard.common.exception.AppException;
import com.siteguard.notify.service.NotifyService;
import com.siteguard.system.controller.AdminConfigController;
import com.siteguard.system.dto.ConfigResponse;
import com.siteguard.system.service.ConfigService;
import com.siteguard.system.enums.ConfigKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminConfigControllerTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final NotifyService notifyService = mock(NotifyService.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final AdminConfigController controller = new AdminConfigController(configService, notifyService, objectMapper);

    @Test
    void get_returnsConfigResponse() throws Exception {
        JsonNode value = objectMapper.readTree("{\"enabled\":true}");
        var node = objectMapper.readTree("{\"enabled\":true}");
        when(configService.getNode(ConfigKey.NOTIFICATION)).thenReturn(node);

        StatusResult<ConfigResponse> result = controller.get("notification");

        assertThat(result.getData().getKey()).isEqualTo("notification");
        assertThat(result.getData().getValue().get("enabled").asBoolean()).isTrue();
    }

    @Test
    void get_invalidKey_throws() {
        assertThatThrownBy(() -> controller.get("unknown-key"))
            .isInstanceOf(AppException.class);
    }

    @Test
    void set_callsServiceSet() throws Exception {
        var params = new com.siteguard.system.dto.ConfigUpdateParams();
        params.setKey("notification");
        params.setValue(objectMapper.readTree("{\"enabled\":true}"));

        controller.set(params);

        verify(configService).set(eq(ConfigKey.NOTIFICATION), any(JsonNode.class));
    }

    @Test
    void delete_callsServiceDelete() {
        var params = new com.siteguard.system.dto.ConfigDeleteParams();
        params.setKey("notification");

        controller.delete(params);

        verify(configService).delete(ConfigKey.NOTIFICATION);
    }

    @Test
    void testWebhook_callsNotifyService() {
        var params = new com.siteguard.notify.dto.TestWebhookParams();
        params.setPlatform(com.siteguard.notify.enums.RobotPlatform.DINGTALK);
        params.setWebhookUrl("https://example.com/hook");
        when(notifyService.testWebhook(any(), any(), any()))
            .thenReturn(new com.siteguard.notify.dto.TestWebhookResult());

        controller.testWebhook(params);

        verify(notifyService).testWebhook(
            com.siteguard.notify.enums.RobotPlatform.DINGTALK,
            "https://example.com/hook",
            null);
    }
}