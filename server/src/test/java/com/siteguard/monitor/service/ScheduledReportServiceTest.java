package com.siteguard.monitor.service;

import com.siteguard.monitor.dto.DashboardResponse;
import com.siteguard.monitor.dto.DashboardSummaryDTO;
import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.notify.service.NotifyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledReportServiceTest {

    @Mock
    SiteCheckService siteCheckService;

    @Mock
    NotifyService notifyService;

    @InjectMocks
    ScheduledReportService service;

    @Test
    void format_containsSummaryAndAlerts() {
        DashboardSummaryDTO summary = new DashboardSummaryDTO(12L, 10L, 1L, 0L, 1L, 142.0);
        AlertDTO alert = new AlertDTO();
        alert.setSiteName("站点A");
        alert.setSiteUrl("https://a.example.com");
        alert.setKind(AlertKind.AVAILABILITY);
        alert.setStatus(AlertStatus.ABNORMAL);
        alert.setMessage("HTTP 500: 连接被拒绝");
        alert.setDetectedAt(1752716520000L);
        DashboardResponse dash = new DashboardResponse(summary, List.of(alert));

        String text = service.format(dash);

        assertTrue(text.contains("站点监控日报"), "应包含标题");
        assertTrue(text.contains("共 12 站"), "应包含总站点数");
        assertTrue(text.contains("健康 10"), "应包含健康数");
        assertTrue(text.contains("异常 1"), "应包含异常数");
        assertTrue(text.contains("暂停 1"), "应包含暂停数");
        assertTrue(text.contains("平均响应 142ms"), "应包含平均响应");
        assertTrue(text.contains("站点A"), "应包含站点名称");
        assertTrue(text.contains("https://a.example.com"), "应包含网址");
        assertTrue(text.contains("HTTP 500: 连接被拒绝"), "应包含消息");
        assertTrue(text.matches("(?s).*发现于 \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.*"), "应包含 yyyy-MM-dd HH:mm:ss 格式的发现时间");
    }

    @Test
    void format_noAlerts_showsAllClear() {
        DashboardSummaryDTO summary = new DashboardSummaryDTO(5L, 5L, 0L, 0L, 0L, null);
        DashboardResponse dash = new DashboardResponse(summary, List.of());

        String text = service.format(dash);

        assertTrue(text.contains("✅ 当前无异常"), "无异常时应显示全清");
    }

    @Test
    void generateAndSend_callsDashboardAndNotify() {
        DashboardSummaryDTO summary = new DashboardSummaryDTO(3L, 3L, 0L, 0L, 0L, 100.0);
        DashboardResponse dash = new DashboardResponse(summary, List.of());
        when(siteCheckService.getDashboard()).thenReturn(dash);

        service.generateAndSend();

        verify(siteCheckService).getDashboard();
        verify(notifyService).send("📊 站点监控日报", service.format(dash));
    }
}
