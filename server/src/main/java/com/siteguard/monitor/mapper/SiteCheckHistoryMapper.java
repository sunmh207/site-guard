package com.siteguard.monitor.mapper;

import com.siteguard.monitor.dto.SiteCheckHistoryDTO;
import com.siteguard.monitor.entity.SiteCheckHistory;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/// 历史实体与 DTO 互转。
///
/// 与 sibling SiteMapper 一致：使用 Spring componentModel，并通过 unmappedTargetPolicy=IGNORE
/// 允许 Entity 演进时 DTO 不必同步演进。
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SiteCheckHistoryMapper {

    SiteCheckHistoryDTO toDTO(SiteCheckHistory entity);
}
