package com.siteguard.monitor.probe;

import com.siteguard.site.entity.Site;

/// 站点探活接口。本期只提供 HttpSiteProbe 一个实现，预留 SSL / WHOIS 等扩展。
public interface SiteProbe {

    /// 对单个站点执行一次探活。实现必须捕获所有异常并以 ProbeResult.error(...) 返回，
    /// 禁止向上抛异常影响 Job 整体。
    ProbeResult probe(Site site);
}
