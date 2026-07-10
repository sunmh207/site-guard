package com.siteguard.site.mapper;

import com.siteguard.site.dto.SiteDTO;
import com.siteguard.site.entity.Site;
import com.siteguard.site.entity.SiteStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SiteMapperTest {

    private final SiteMapper mapper = Mappers.getMapper(SiteMapper.class);

    @Test
    void toDTO_mapsAllFields() {
        var site = new Site();
        site.setName("官网");
        site.setUrl("https://example.com");
        site.setAvailabilityStatus(SiteStatus.UP);
        site.setCertificateIssuer("Let's Encrypt");

        SiteDTO dto = mapper.toDTO(site);

        assertNotNull(dto);
        assertEquals("官网", dto.getName());
        assertEquals("https://example.com", dto.getUrl());
        assertEquals(SiteStatus.UP, dto.getAvailabilityStatus());
        assertEquals("Let's Encrypt", dto.getCertificateIssuer());
    }

    @Test
    void toDTO_nullStatusMapsToNull() {
        var site = new Site();
        site.setName("官网");
        site.setUrl("https://example.com");

        SiteDTO dto = mapper.toDTO(site);

        assertNotNull(dto);
        assertNull(dto.getAvailabilityStatus());
    }

    @Test
    void toRows_mapsList() {
        var a = new Site();
        a.setName("A");
        a.setUrl("https://a.example.com");
        var b = new Site();
        b.setName("B");
        b.setUrl("https://b.example.com");

        var rows = mapper.toRows(List.of(a, b));

        assertNotNull(rows);
        assertEquals(2, rows.size());
        assertEquals("A", rows.get(0).getName());
        assertEquals("B", rows.get(1).getName());
    }
}