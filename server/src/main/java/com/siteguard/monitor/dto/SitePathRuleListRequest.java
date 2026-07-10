package com.siteguard.monitor.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/// 整批 set 规则请求体。
///
/// set 端点语义：删除该 site_id 下的全部旧规则，按本请求的 rules 列表全量插入。
/// - 前端在编辑已有规则时仍可能把原 id 一并发回——后端会忽略这些 id，由数据库重新分配。
///   这样既避免 JPA 在批量 DELETE 之后 merge 时抛 StaleObjectStateException，
///   也防止前端尝试"复活"已被并发删除的 id。
/// - 后端不做 diff，按"全删全插"处理；不按 id 做增量 upsert，也不按 path 做 diff 合并。
/// 简而言之：全删全插（atomic replace），id 始终由数据库自增分配。
public record SitePathRuleListRequest(
        @NotNull Long siteId,
        @NotEmpty List<SitePathRuleDTO> rules
) {}
