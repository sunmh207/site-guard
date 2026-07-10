package com.siteguard.api.admin;

import com.siteguard.common.dto.IdPayload;
import com.siteguard.common.dto.PagerPayload;
import com.siteguard.common.dto.StatusResult;
import com.siteguard.monitor.dto.DashboardResponse;
import com.siteguard.monitor.dto.SiteCheckHistoryDTO;
import com.siteguard.monitor.service.SiteCheckService;
import com.siteguard.site.dto.SiteCreateParams;
import com.siteguard.site.dto.SiteDTO;
import com.siteguard.site.dto.SiteMoveParams;
import com.siteguard.site.dto.SitePauseParams;
import com.siteguard.site.dto.SiteSearchParams;
import com.siteguard.site.dto.SiteUpdateParams;
import com.siteguard.site.service.SiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// 站点管理后台接口（CRUD）
///
/// 路径：/api/v1/admin/site
/// 设计风格：仅使用 GET 与 POST，按动作命名（search / get / create / update / delete）。
/// 该风格借鉴自 edusoho-lms 项目，与本仓库现有的 AdminUserController 保持一致。
@RestController
@RequestMapping("/api/v1/admin/site")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "站点管理", description = "后台站点管理相关接口")
public class AdminSiteController {

    private final SiteService siteService;
    private final SiteCheckService siteCheckService;

    /// 站点分页搜索
    @Operation(summary = "分页搜索站点")
    @GetMapping("/search")
    public PagerPayload<SiteDTO> search(SiteSearchParams params,
                                        @PageableDefault(sort = { "createdAt" }, size = 20, direction = Sort.Direction.DESC) Pageable pager) {
        var sites = siteService.search(params, pager);
        return new PagerPayload<>(sites, pager);
    }

    /// 站点详情（按 ID）
    @Operation(summary = "获取站点详情")
    @GetMapping("/get")
    public SiteDTO get(
            @RequestParam @Parameter(description = "站点 ID", required = true) Long id) {
        return siteService.getDetail(id);
    }

    /// 创建站点（HTTP 201）
    @Operation(summary = "创建站点")
    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public SiteDTO create(@Valid @RequestBody SiteCreateParams params) {
        return siteService.create(params);
    }

    /// 更新站点
    @Operation(summary = "更新站点")
    @PostMapping("/update")
    public SiteDTO update(@Valid @RequestBody SiteUpdateParams params) {
        return siteService.update(params);
    }

    /// 删除站点
    @Operation(summary = "删除站点")
    @PostMapping("/delete")
    public StatusResult<Void> delete(@Valid @RequestBody IdPayload payload) {
        siteService.delete(payload.getId());
        return StatusResult.ok();
    }

    /// 设置站点暂停状态
    @Operation(summary = "设置站点暂停状态")
    @PostMapping("/set-paused")
    public SiteDTO setPaused(@Valid @RequestBody SitePauseParams payload) {
        return siteService.setPaused(payload.getId(), payload.getPaused());
    }

    /// 批量移动站点到目标分类（拖拽落点）
    @Operation(summary = "批量移动站点到分类")
    @PostMapping("/move")
    public StatusResult<Integer> move(@Valid @RequestBody SiteMoveParams params) {
        return StatusResult.success(siteService.moveSites(params.getSiteIds(), params.getCategoryId()));
    }

    /// 仪表盘聚合（4 卡片 + 最近异常列表）
    @Operation(summary = "仪表盘聚合")
    @GetMapping("/stats/dashboard")
    public DashboardResponse dashboard() {
        return siteCheckService.getDashboard();
    }

    /// 站点最近探测历史（按 checked_at 倒序）。
    /// - 路径风格：嵌套在 /site/{siteId}/... 下，参照 /site/{siteId}/pathRules/get
    /// - limit 默认 30；服务端在 service 层做了 30 的硬上限钳制，前端无需校验
    /// - 不分页：返回固定数量的最新记录，UI 用固定 30 条 slideover 表格展示
    @Operation(summary = "站点最近探测历史")
    @GetMapping("/{siteId}/history/get")
    public List<SiteCheckHistoryDTO> recentHistory(
            @PathVariable @Parameter(description = "站点 ID", required = true) Long siteId,
            @RequestParam(defaultValue = "30") @Parameter(description = "返回条数，默认 30，最大 30") int limit) {
        return siteCheckService.listRecent(siteId, limit);
    }
}
