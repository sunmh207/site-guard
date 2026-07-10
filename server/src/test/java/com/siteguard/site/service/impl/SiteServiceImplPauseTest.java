package com.siteguard.site.service.impl;

import com.siteguard.category.service.CategoryService;
import com.siteguard.common.exception.AppException;
import com.siteguard.monitor.repository.SiteCheckHistoryRepository;
import com.siteguard.site.entity.Site;
import com.siteguard.site.entity.SiteStatus;
import com.siteguard.site.mapper.SiteMapper;
import com.siteguard.site.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteServiceImplPauseTest {

    @Mock SiteRepository repo;
    @Mock SiteMapper mapper;
    @Mock SiteCheckHistoryRepository historyRepo;
    @Mock CategoryService categoryService;

    @InjectMocks SiteServiceImpl service;

    private Site existing;

    @BeforeEach
    void setUp() {
        existing = new Site();
        existing.setId(1L);
        existing.setName("test");
        existing.setUrl("https://example.com");
        existing.setCategoryId(1L);
        existing.setAvailabilityStatus(SiteStatus.UP);
        existing.setPaused(false);
    }

    @Test
    void setPaused_writesTrue_persistsAndReturnsDto() {
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDTO(any(Site.class))).thenAnswer(inv -> {
            var s = inv.getArgument(0, Site.class);
            var dto = new com.siteguard.site.dto.SiteDTO();
            dto.setId(s.getId());
            dto.setPaused(s.isPaused());
            return dto;
        });

        var dto = service.setPaused(1L, true);

        assertThat(dto.isPaused()).isTrue();
        assertThat(existing.isPaused()).isTrue();
        verify(repo).save(existing);
    }

    @Test
    void setPaused_writesFalse_resumesSite() {
        existing.setPaused(true);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDTO(any(Site.class))).thenAnswer(inv -> {
            var s = inv.getArgument(0, Site.class);
            var dto = new com.siteguard.site.dto.SiteDTO();
            dto.setId(s.getId());
            dto.setPaused(s.isPaused());
            return dto;
        });

        var dto = service.setPaused(1L, false);

        assertThat(dto.isPaused()).isFalse();
        assertThat(existing.isPaused()).isFalse();
    }

    @Test
    void setPaused_sameValue_isIdempotent() {
        existing.setPaused(true);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDTO(any(Site.class))).thenAnswer(inv -> {
            var s = inv.getArgument(0, Site.class);
            var dto = new com.siteguard.site.dto.SiteDTO();
            dto.setId(s.getId());
            dto.setPaused(s.isPaused());
            return dto;
        });

        // 重复设同一个值不应抛错
        var dto = service.setPaused(1L, true);

        assertThat(dto.isPaused()).isTrue();
        verify(repo).save(existing);
    }

    @Test
    void setPaused_notFound_throwsAppException() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPaused(99L, true))
                .isInstanceOf(AppException.class);

        verify(repo, never()).save(any());
    }
}
