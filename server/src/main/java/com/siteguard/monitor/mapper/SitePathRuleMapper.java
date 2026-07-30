package com.siteguard.monitor.mapper;

import com.siteguard.monitor.dto.SitePathRuleDTO;
import com.siteguard.monitor.entity.SitePathRule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/// 站点子路由规则实体映射器（MapStruct）
///
/// 负责 SitePathRule Entity 与 SitePathRuleDTO 之间的转换。
/// 未映射的目标字段将被忽略（unmappedTargetPolicy=IGNORE），允许 Entity 演进时 DTO 不必同步演进。
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SitePathRuleMapper {

    /// SitePathRule → SitePathRuleDTO；assertionConfig 由 service 通过 codec 解码。
    @Mapping(target = "assertionConfig", ignore = true)
    SitePathRuleDTO toDTO(SitePathRule rule);

    /// 列表转换
    List<SitePathRuleDTO> toRows(List<SitePathRule> rules);

    /// DTO → Entity。结构化 assertionConfig 由 service 通过 codec 编码，避免隐式对象→字符串映射。
    /// 探测状态字段 last_* 仍由调用方在 mapper 后强制清空，防止前端伪造结果。
    @Mapping(target = "assertionConfig", ignore = true)
    SitePathRule toEntity(SitePathRuleDTO dto);
}
