package com.siteguard.domain.service;

import com.siteguard.site.entity.Site;

/// 域名到期检测的服务入口。
///
/// - checkAll: Quartz Job 调用入口，并发探测所有站点
/// - checkOne: 单个站点的域名探测 + 更新快照；任何步骤的异常都被吞掉
public interface DomainCheckService {

    /// 并发探测所有站点的域名到期日（虚拟线程，单个站点失败不影响其他站点）。
    void checkAll();

    /// 对单个站点查询域名到期日并更新快照。本方法不抛任何异常。
    /// site 为 null 时直接返回。
    void checkOne(Site site);
}
