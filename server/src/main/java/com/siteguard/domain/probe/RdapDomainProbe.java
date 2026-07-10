package com.siteguard.domain.probe;

import com.siteguard.domain.rdap.RdapClient;
import com.siteguard.site.entity.Site;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;

/// DomainProbe 的 RDAP 实现。
///
/// - 启动时拉一次 IANA bootstrap（失败 log error，不抛）
/// - probe(site)：从 site.url 提取 host（含子域），委托给 RdapClient.lookup(host)
/// - 所有异常一律转成 DomainProbeResult.failed()，不向上抛
@Component
@RequiredArgsConstructor
@Slf4j
public class RdapDomainProbe implements DomainProbe {

    private final RdapClient rdapClient;

    /// 启动时拉 bootstrap。失败时 RdapClient.bootstrap 保持 null，
    /// 后续 lookup 全部返回 null（不抛），由 service 层决定怎么处理。
    @PostConstruct
    void init() {
        try {
            rdapClient.initBootstrap();
        } catch (RuntimeException e) {
            log.warn("RdapClient bootstrap init threw: {}", e.getMessage());
        }
    }

    @Override
    public DomainProbeResult probe(Site site) {
        if (site == null || site.getUrl() == null) {
            return DomainProbeResult.failed();
        }
        String host;
        try {
            host = URI.create(site.getUrl()).getHost();
        } catch (IllegalArgumentException e) {
            log.debug("Invalid site url '{}': {}", site.getUrl(), e.getMessage());
            return DomainProbeResult.failed();
        }
        if (host == null || host.isBlank()) {
            return DomainProbeResult.failed();
        }
        Long expires = rdapClient.lookup(host);
        return expires != null ? DomainProbeResult.ok(expires) : DomainProbeResult.failed();
    }
}