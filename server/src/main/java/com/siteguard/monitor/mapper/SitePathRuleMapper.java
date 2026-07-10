package com.siteguard.monitor.mapper;

import com.siteguard.monitor.dto.SitePathRuleDTO;
import com.siteguard.monitor.entity.SitePathRule;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/// 站点子路由规则实体映射器（MapStruct）
///
/// 负责 SitePathRule Entity 与 SitePathRuleDTO 之间的转换。
/// 未映射的目标字段将被忽略（unmappedTargetPolicy=IGNORE），允许 Entity 演进时 DTO 不必同步演进。
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SitePathRuleMapper {

    /// SitePathRule → SitePathRuleDTO
    SitePathRuleDTO toDTO(SitePathRule rule);

    /// 列表转换
    List<SitePathRuleDTO> toRows(List<SitePathRule> rules);

    /// DTO → Entity（注意：探测状态字段 last_* 必须由调用方在 mapper 调用之后强制置 null，
    /// 防止前端伪造历史探测结果；MapStruct 自动映射会复制 DTO 上的 last_* 字段）
    SitePathRule toEntity(SitePathRuleDTO dto);
}
