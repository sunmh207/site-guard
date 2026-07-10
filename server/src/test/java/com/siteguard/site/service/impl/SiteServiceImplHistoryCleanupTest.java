package com.siteguard.site.service.impl;

import com.siteguard.monitor.repository.SiteCheckHistoryRepository;
import com.siteguard.site.entity.Site;
import com.siteguard.site.mapper.SiteMapper;
import com.siteguard.site.repository.SiteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteServiceImplHistoryCleanupTest {

    @Mock
    SiteRepository repo;

    @Mock
    SiteMapper mapper;

    @Mock
    SiteCheckHistoryRepository historyRepo;

    @InjectMocks
    SiteServiceImpl service;

    @Test
    void delete_cleansUpHistoryBeforeDeletingSite() {
        when(repo.findById(1L)).thenReturn(Optional.of(new Site()));

        service.delete(1L);

        verify(historyRepo).deleteBySiteId(1L);
        verify(repo).delete(org.mockito.ArgumentMatchers.any(Site.class));
    }

    @Test
    void delete_historyCleanupFailureSkipped_siteStillDeleted() {
        when(repo.findById(1L)).thenReturn(Optional.of(new Site()));
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(historyRepo).deleteBySiteId(1L);

        try {
            service.delete(1L);
        } catch (RuntimeException ignored) {
            // 期望：不抛
        }

        // 站点删除必须仍然发生（历史清理失败不应阻塞主操作）
        verify(repo).delete(org.mockito.ArgumentMatchers.any(Site.class));
    }

    @Test
    void delete_siteNotFound_historyNotTouched() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        try {
            service.delete(99L);
        } catch (RuntimeException ignored) {
            // 期望：站点不存在时抛 NOT_FOUND，但仍不应触发历史清理
        }

        verify(historyRepo, never()).deleteBySiteId(org.mockito.ArgumentMatchers.anyLong());
    }
}
