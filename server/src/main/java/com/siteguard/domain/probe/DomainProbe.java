package com.siteguard.domain.probe;

import com.siteguard.site.entity.Site;

/// 域名到期查询接口。本期只提供 RdapDomainProbe 一个实现。
///
/// 实现必须捕获所有异常并以 DomainProbeResult.failed() 返回，
/// 禁止向上抛异常影响 Job 整体。
public interface DomainProbe {

    /// 对单个站点的 URL 执行一次 RDAP 查询，返回到期日（毫秒时间戳）或 null。
    DomainProbeResult probe(Site site);
}