package com.siteguard.domain.service.impl;

import com.siteguard.domain.probe.DomainProbe;
import com.siteguard.domain.probe.DomainProbeResult;
import com.siteguard.site.entity.Site;
import com.siteguard.site.entity.SiteStatus;
import com.siteguard.site.repository.SiteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class DomainCheckServiceImplTest {

    @Mock
    DomainProbe probe;

    @Mock
    SiteRepository siteRepo;

    @org.mockito.InjectMocks
    DomainCheckServiceImpl service;

    private Site newSite() {
        var s = new Site();
        s.setId(1L);
        s.setName("test");
        s.setUrl("https://example.com");
        s.setAvailabilityStatus(SiteStatus.UNKNOWN);
        return s;
    }

    @Test
    void checkOne_success_writesDomainExpiresAt() {
        var site = newSite();
        when(probe.probe(site)).thenReturn(DomainProbeResult.ok(1735689600000L));

        service.checkOne(site);

        assertEquals(1735689600000L, site.getDomainExpiresAt());
        verify(siteRepo).save(site);
    }

    @Test
    void checkOne_failed_doesNotOverwrite() {
        var site = newSite();
        // 站点之前已有域名到期日
        site.setDomainExpiresAt(1735689600000L);
        when(probe.probe(site)).thenReturn(DomainProbeResult.failed());

        service.checkOne(site);

        // 探测失败：保持原值，不覆盖为 null
        assertEquals(1735689600000L, site.getDomainExpiresAt());
        verify(siteRepo, never()).save(any());
    }

    @Test
    void checkOne_probeThrows_doesNotPropagate() {
        var site = newSite();
        when(probe.probe(site)).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> service.checkOne(site));

        verify(siteRepo, never()).save(any());
        assertNull(site.getDomainExpiresAt());
    }

    @Test
    void checkOne_saveFails_doesNotPropagate() {
        var site = newSite();
        when(probe.probe(site)).thenReturn(DomainProbeResult.ok(1735689600000L));
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(siteRepo).save(any());

        assertDoesNotThrow(() -> service.checkOne(site));
    }

    @Test
    void checkAll_emptyList_returnsEarly() {
        when(siteRepo.findAll(any(Sort.class))).thenReturn(List.of());

        assertDoesNotThrow(() -> service.checkAll());

        verify(probe, never()).probe(any());
    }

    @Test
    void checkAll_invokesProbeForEachSite() {
        var s1 = newSite();
        s1.setId(1L);
        var s2 = newSite();
        s2.setId(2L);
        when(siteRepo.findAll(any(Sort.class))).thenReturn(List.of(s1, s2));
        when(probe.probe(any())).thenReturn(DomainProbeResult.failed());

        service.checkAll();

        verify(probe).probe(s1);
        verify(probe).probe(s2);
    }

    @Test
    void checkOne_nullSite_doesNotThrow() {
        assertDoesNotThrow(() -> service.checkOne(null));
        verify(probe, never()).probe(any());
    }
}
