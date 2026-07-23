package com.siteguard.api.admin;

import com.siteguard.common.dto.IdPayload;
import com.siteguard.common.dto.StatusResult;
import com.siteguard.monitor.dto.SitePathCheckHistoryDTO;
import com.siteguard.monitor.dto.SitePathRuleDTO;
import com.siteguard.monitor.dto.SitePathRuleListRequest;
import com.siteguard.monitor.service.SitePathRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// 站点子路由规则管理接口。
///
/// 路径：/api/v1/admin/site/{siteId}/pathRules 与 /api/v1/admin/site/pathRule
/// 设计风格遵循项目惯例：仅 GET/POST，按动作命名。
@RestController
@RequestMapping("/api/v1/admin/site")
@RequiredArgsConstructor
@Tag(name = "站点子路由规则", description = "自定义子路由检测规则的 CRUD")
public class AdminSitePathRuleController {

    private final SitePathRuleService service;

    /// 列出某站点的全部子路由规则
    @Operation(summary = "列出某站点的全部子路由规则")
    @GetMapping("/{siteId}/pathRules/get")
    public StatusResult<List<SitePathRuleDTO>> list(@PathVariable Long siteId) {
        return StatusResult.success(service.listBySite(siteId));
    }

    /// 整批覆盖某站点的子路由规则（"全删全插"语义）
    @Operation(summary = "整批覆盖某站点的子路由规则")
    @PostMapping("/{siteId}/pathRules/set")
    public StatusResult<Void> set(@PathVariable Long siteId,
                                  @Valid @RequestBody SitePathRuleListRequest body) {
        // path 上的 siteId 优先；body 里的 siteId 与服务端值不一致时以路径为准
        var req = new SitePathRuleListRequest(siteId, body.rules());
        service.set(req);
        return StatusResult.ok();
    }

    /// 按 id 删除单条规则
    @Operation(summary = "按 id 删除单条规则")
    @PostMapping("/pathRule/delete")
    public StatusResult<Void> delete(@Valid @RequestBody IdPayload payload) {
        service.delete(payload.getId());
        return StatusResult.ok();
    }

    /// 某条路径规则的最近探测历史（按 checked_at 倒序）。
    /// - 路径风格：与 /pathRule/delete 一致，按动作命名；ruleId 已唯一标识规则，不再嵌套 siteId
    /// - limit 默认 30；服务端在 service 层做了 30 的硬上限钳制
    /// - 不分页：返回固定数量的最新记录，UI 用 slideover 表格展示
    @Operation(summary = "某条路径规则的最近探测历史")
    @GetMapping("/pathRule/history/get")
    public List<SitePathCheckHistoryDTO> recentHistory(
            @RequestParam @Parameter(description = "路径规则 ID", required = true) Long ruleId,
            @RequestParam(defaultValue = "30") @Parameter(description = "返回条数，默认 30，最大 30") int limit) {
        return service.listRecentHistory(ruleId, limit);
    }
}
