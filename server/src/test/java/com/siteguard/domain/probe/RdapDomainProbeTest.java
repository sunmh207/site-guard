package com.siteguard.domain.probe;

import com.siteguard.site.entity.Site;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RdapDomainProbeTest {

    @Mock
    com.siteguard.domain.rdap.RdapClient client;

    @org.mockito.InjectMocks
    RdapDomainProbe probe;

    private Site site(String url) {
        var s = new Site();
        s.setId(1L);
        s.setName("test");
        s.setUrl(url);
        return s;
    }

    @Test
    void probe_extractsHostAndDelegatesToClient() {
        var s = site("https://api.example.com/path?q=1");
        when(client.lookup("api.example.com")).thenReturn(1735689600000L);

        var result = probe.probe(s);

        // 委托 client.lookup 取完整 host（含子域）
        verify(client).lookup("api.example.com");
        assert result != null;
        assert result.domainExpiresAt() == 1735689600000L;
    }

    @Test
    void probe_invalidUrl_returnsFailed() {
        var s = new Site();
        s.setId(1L);
        s.setName("test");
        s.setUrl("not a url");

        var result = probe.probe(s);

        assert result != null;
        assertNull(result.domainExpiresAt());
    }

    @Test
    void probe_nullUrl_returnsFailed() {
        var s = new Site();
        s.setId(1L);
        s.setName("test");
        // url is null

        var result = probe.probe(s);

        assertNull(result.domainExpiresAt());
    }

    @Test
    void probe_clientReturnsNull_returnsFailed() {
        var s = site("https://example.com");
        when(client.lookup(any())).thenReturn(null);

        var result = probe.probe(s);

        assertNull(result.domainExpiresAt());
    }
}