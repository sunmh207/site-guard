package com.siteguard.site.mapper;

import com.siteguard.monitor.probe.CertForgive;
import com.siteguard.monitor.probe.CertForgiveType;
import com.siteguard.site.dto.SiteDTO;
import com.siteguard.site.entity.MaintenanceStatus;
import com.siteguard.site.entity.Site;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/// 站点实体映射器（MapStruct）
///
/// 负责 Site Entity 与 SiteDTO 之间的转换。
/// 未映射的目标字段将被忽略（unmappedTargetPolicy=IGNORE），允许 Entity 演进时 DTO 不必同步演进。
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class SiteMapper {

    /// 注入 Clock,供 fillMaintenanceStatus 计算"此刻是否落在运维窗口"。
    /// 生产由 Spring 注入 MonitorTimeConfig.clock();
    /// 测试走 Mappers.getMapper() 绕过 Spring 时 clock 未注入 — fillMaintenanceStatus 会回退到系统默认时钟,
    /// 不会出现 NPE 且与生产服务器时区一致。
    @Autowired
    protected Clock clock;

    /// Site → SiteDTO 主字段。cert_forgive 三个开关 + maintenanceStatus 在 @AfterMapping 填充。
    @AfterMapping
    protected void fillCertForgive(Site site, @MappingTarget SiteDTO dto) {
        Set<CertForgiveType> types = site.getCertForgiveTypes();
        dto.setCertForgiveChainIncomplete(types.contains(CertForgiveType.CHAIN_INCOMPLETE));
        dto.setCertForgiveDomainMismatch(types.contains(CertForgiveType.DOMAIN_MISMATCH));
        dto.setCertForgiveSelfSigned(types.contains(CertForgiveType.SELF_SIGNED));
    }

    /// 计算站点此刻运维时段运行态。默认 [SiteMaintenance#DEFAULT_ZONE](Asia/Shanghai)。
    /// 站点未启用运维时段 → MaintenanceStatus.NONE;已配置则按此刻时间判定 ACTIVE / SCHEDULED。
    @AfterMapping
    protected void fillMaintenanceStatus(Site site, @MappingTarget SiteDTO dto) {
        // clock 未注入时(纯 new / Mappers.getMapper 绕过 Spring)回退到系统默认时钟,避免 NPE。
        // MaintenanceStatus.of 接受 Instant,内部按 DEFAULT_ZONE(Asia/Shanghai) 解读 wall-clock 时间。
        Clock c = clock != null ? clock : Clock.systemDefaultZone();
        dto.setMaintenanceStatus(MaintenanceStatus.of(site, Instant.now(c)));
    }

    /// Site → SiteDTO（MapStruct 自动生成字段复制；@AfterMapping 负责 cert_forgive + maintenanceStatus）
    public abstract SiteDTO toDTO(Site site);

    /// Site 列表 → SiteDTO 列表
    public abstract List<SiteDTO> toRows(List<Site> sites);
}
