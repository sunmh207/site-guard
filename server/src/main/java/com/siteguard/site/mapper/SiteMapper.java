package com.siteguard.site.mapper;

import com.siteguard.site.dto.SiteDTO;
import com.siteguard.site.entity.Site;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/// 站点实体映射器（MapStruct）
///
/// 负责 Site Entity 与 SiteDTO 之间的转换。
/// 未映射的目标字段将被忽略（unmappedTargetPolicy=IGNORE），允许 Entity 演进时 DTO 不必同步演进。
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SiteMapper {

    /// Site → SiteDTO
    SiteDTO toDTO(Site site);

    /// Site 列表 → SiteDTO 列表
    List<SiteDTO> toRows(List<Site> sites);
}