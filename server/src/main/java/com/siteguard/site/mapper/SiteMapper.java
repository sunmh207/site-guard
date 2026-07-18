package com.siteguard.site.mapper;

import com.siteguard.monitor.probe.CertForgive;
import com.siteguard.monitor.probe.CertForgiveType;
import com.siteguard.site.dto.SiteDTO;
import com.siteguard.site.entity.Site;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.Set;

/// 站点实体映射器（MapStruct）
///
/// 负责 Site Entity 与 SiteDTO 之间的转换。
/// 未映射的目标字段将被忽略（unmappedTargetPolicy=IGNORE），允许 Entity 演进时 DTO 不必同步演进。
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class SiteMapper {

    /// Site → SiteDTO 主字段。cert_forgive 三个开关在 @AfterMapping 填充。
    @AfterMapping
    protected void fillCertForgive(Site site, @MappingTarget SiteDTO dto) {
        Set<CertForgiveType> types = site.getCertForgiveTypes();
        dto.setCertForgiveChainIncomplete(types.contains(CertForgiveType.CHAIN_INCOMPLETE));
        dto.setCertForgiveDomainMismatch(types.contains(CertForgiveType.DOMAIN_MISMATCH));
        dto.setCertForgiveSelfSigned(types.contains(CertForgiveType.SELF_SIGNED));
    }

    /// Site → SiteDTO（MapStruct 自动生成字段复制；@AfterMapping 负责 cert_forgive）
    public abstract SiteDTO toDTO(Site site);

    /// Site 列表 → SiteDTO 列表
    public abstract List<SiteDTO> toRows(List<Site> sites);
}
