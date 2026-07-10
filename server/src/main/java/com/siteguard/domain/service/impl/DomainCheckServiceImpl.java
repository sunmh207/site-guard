package com.siteguard.domain.service.impl;

import com.siteguard.domain.probe.DomainProbe;
import com.siteguard.domain.service.DomainCheckService;
import com.siteguard.site.entity.Site;
import com.siteguard.site.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/// 域名到期检测服务实现。
///
/// - checkAll: 拉全量站点 → 虚拟线程并发探测 → 仅成功时更新 site.domainExpiresAt
/// - checkOne: 单站点的探测 + 落库；任何步骤异常都被捕获并 log，绝不向上抛
///
/// 关键不变量：探测失败时不动 site.domainExpiresAt（避免被限速/隐私保护时把已有数据抹掉）。
/// Service 层不抛任何业务异常。
@Service
@RequiredArgsConstructor
@Slf4j
public class DomainCheckServiceImpl implements DomainCheckService {

    private final DomainProbe domainProbe;
    private final SiteRepository siteRepo;

    @Override
    public void checkAll() {
        var sites = siteRepo.findAll(Sort.by(Sort.Direction.ASC, "id"));
        if (sites.isEmpty()) {
            return;
        }
        // try-with-resources 保证 executor 被关闭；Java 25 虚拟线程
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = sites.stream()
                    .map(site -> CompletableFuture.runAsync(() -> checkOne(site), executor))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();
        }
        log.debug("Domain check completed for {} sites", sites.size());
    }

    @Override
    public void checkOne(Site site) {
        if (site == null) {
            return;
        }
        try {
            var result = domainProbe.probe(site);
            // 失败时不覆盖 site.domainExpiresAt
            if (result.domainExpiresAt() == null) {
                return;
            }
            site.setDomainExpiresAt(result.domainExpiresAt());
            try {
                siteRepo.save(site);
            } catch (RuntimeException e) {
                log.warn("Failed to update domain expiry for site {}: {}", site.getId(), e.getMessage());
            }
        } catch (RuntimeException e) {
            log.warn("Unexpected failure while checking domain for site {}: {}",
                    site.getId(), e.getMessage());
        }
    }
}
