package com.siteguard.api.open;

import com.siteguard.common.exception.Errors;
import com.siteguard.monitor.dto.DashboardResponse;
import com.siteguard.monitor.service.SiteCheckService;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 公开 dashboard 端点（仅内网大屏使用）。
///
/// 委托给 siteCheckService.getDashboard()，与 admin 端共享同一聚合服务，
/// 避免双源真相。SecurityConfig 已对 /api/v1/open/** 放行，
/// 此 controller 不需要也不允许任何写/管理能力。
///
/// 访问闸门：默认走 ConfigKey.OPEN_DASHBOARD=false，未开启时抛 NOT_FOUND。
/// 用意：「隐式」开关被显式化——admin 在 设置 → 显示 页面切到 true 才放出数据。
/// 默认 null（DB 无记录）/ false 两种情况都被 getOrDefault 收口到 false。
@RestController
@RequestMapping("/api/v1/open/site")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "公开看板", description = "无需鉴权、用于内网大屏展示")
public class OpenDashboardController {
    private final SiteCheckService siteCheckService;
    private final ConfigService configService;

    @GetMapping("/stats/dashboard")
    @Operation(summary = "公开仪表盘聚合", description = "需 admin 在 设置 → 显示 中开启后才会返回数据")
    public DashboardResponse dashboard() {
        /// getOrDefault 让"DB 无记录（null）"与"显式设为 false"都走关闭分支，
        /// 与"DB 显式 true"区分开。统一在一个调用点判定，避免散落在多个方法内。
        if (!configService.getOrDefault(ConfigKey.OPEN_DASHBOARD, false)) {
            throw Errors.NOT_FOUND.toException("公开大屏未开启");
        }
        return siteCheckService.getDashboard();
    }
}