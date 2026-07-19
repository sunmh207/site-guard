package com.siteguard.site.service.impl;

import com.siteguard.category.service.CategoryService;
import com.siteguard.common.exception.AppException;
import com.siteguard.monitor.repository.SiteCheckHistoryRepository;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.dto.SiteCreateParams;
import com.siteguard.site.dto.SiteDTO;
import com.siteguard.site.dto.SiteUpdateParams;
import com.siteguard.site.entity.Site;
import com.siteguard.site.mapper.SiteMapper;
import com.siteguard.site.repository.SiteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteServiceImplTest {

    @Mock
    SiteRepository repo;

    @Mock
    SiteMapper mapper;

    @Mock
    SiteCheckHistoryRepository historyRepo;

    @Mock
    SitePathRuleRepository pathRuleRepo;

    @Mock
    CategoryService categoryService;

    @InjectMocks
    SiteServiceImpl service;

    @Test
    void create_ok() {
        var params = new SiteCreateParams();
        params.setName("官网");
        params.setUrl("https://example.com");

        when(repo.existsByName("官网")).thenReturn(false);
        when(repo.existsByUrl("https://example.com")).thenReturn(false);
        // 未传 categoryId 时落入默认分类
        when(categoryService.defaultCategoryId()).thenReturn(1L);
        when(repo.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDTO(any(Site.class))).thenAnswer(inv -> {
            var s = (Site) inv.getArgument(0);
            var dto = new SiteDTO();
            dto.setId(1L);
            dto.setName(s.getName());
            dto.setUrl(s.getUrl());
            return dto;
        });

        var dto = service.create(params);

        assertNotNull(dto);
        assertEquals("官网", dto.getName());
        assertEquals("https://example.com", dto.getUrl());
        verify(repo).save(any(Site.class));
    }

    @Test
    void create_duplicateName_throws() {
        var params = new SiteCreateParams();
        params.setName("官网");
        params.setUrl("https://example.com");

        when(repo.existsByName("官网")).thenReturn(true);

        assertThrows(AppException.class, () -> service.create(params));
        verify(repo, never()).save(any(Site.class));
    }

    @Test
    void create_duplicateUrl_throws() {
        var params = new SiteCreateParams();
        params.setName("官网");
        params.setUrl("https://example.com");

        when(repo.existsByName("官网")).thenReturn(false);
        when(repo.existsByUrl("https://example.com")).thenReturn(true);

        assertThrows(AppException.class, () -> service.create(params));
        verify(repo, never()).save(any(Site.class));
    }

    @Test
    void update_ok() {
        var site = new Site();
        site.setId(1L);
        site.setName("旧名");
        site.setUrl("https://old.example.com");
        when(repo.findById(1L)).thenReturn(Optional.of(site));
        when(repo.existsByNameAndIdNot("新名", 1L)).thenReturn(false);
        when(repo.existsByUrlAndIdNot("https://new.example.com", 1L)).thenReturn(false);
        when(repo.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDTO(any(Site.class))).thenAnswer(inv -> {
            var s = (Site) inv.getArgument(0);
            var dto = new SiteDTO();
            dto.setId(s.getId());
            dto.setName(s.getName());
            dto.setUrl(s.getUrl());
            return dto;
        });

        var params = new SiteUpdateParams();
        params.setId(1L);
        params.setName("新名");
        params.setUrl("https://new.example.com");

        var dto = service.update(params);

        assertNotNull(dto);
        assertEquals("新名", site.getName());
        assertEquals("https://new.example.com", site.getUrl());
        verify(repo).save(any(Site.class));
    }

    @Test
    void update_siteNotFound_throws() {
        var params = new SiteUpdateParams();
        params.setId(99L);
        params.setName("新名");
        params.setUrl("https://new.example.com");

        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> service.update(params));
        verify(repo, never()).save(any(Site.class));
    }

    @Test
    void update_duplicateName_throws() {
        var site = new Site();
        site.setId(1L);
        site.setName("旧名");
        site.setUrl("https://old.example.com");
        when(repo.findById(1L)).thenReturn(Optional.of(site));
        when(repo.existsByNameAndIdNot("冲突名", 1L)).thenReturn(true);

        var params = new SiteUpdateParams();
        params.setId(1L);
        params.setName("冲突名");
        params.setUrl("https://new.example.com");

        assertThrows(AppException.class, () -> service.update(params));
    }

    @Test
    void delete_ok() {
        when(repo.findById(1L)).thenReturn(Optional.of(new Site()));
        when(historyRepo.deleteBySiteId(1L)).thenReturn(0L);
        when(pathRuleRepo.deleteBySiteId(1L)).thenReturn(0L);

        service.delete(1L);

        verify(historyRepo).deleteBySiteId(1L);
        verify(pathRuleRepo).deleteBySiteId(1L);
        verify(repo).delete(any(Site.class));
    }

    @Test
    void delete_cascadesToPathRules() {
        when(repo.findById(1L)).thenReturn(Optional.of(new Site()));
        when(historyRepo.deleteBySiteId(1L)).thenReturn(0L);
        when(pathRuleRepo.deleteBySiteId(1L)).thenReturn(3L);

        service.delete(1L);

        // 路径规则清理在站点删除之前发生
        verify(pathRuleRepo).deleteBySiteId(1L);
        verify(repo).delete(any(Site.class));
    }

    @Test
    void delete_pathRuleCleanupFailure_doesNotBlock() {
        when(repo.findById(1L)).thenReturn(Optional.of(new Site()));
        when(historyRepo.deleteBySiteId(1L)).thenReturn(0L);
        // 规则清理抛错不应阻塞主流程
        when(pathRuleRepo.deleteBySiteId(1L)).thenThrow(new RuntimeException("db down"));

        service.delete(1L);

        // 站点删除仍应执行
        verify(repo).delete(any(Site.class));
    }

    @Test
    void delete_notFound_throws() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> service.delete(99L));
        verify(repo, never()).delete(any(Site.class));
    }

    @Test
    void getDetail_notFound_throws() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> service.getDetail(99L));
    }

    /// create 启用运维时段:services 解析后写入 site.maintenance,且格式被规范化。
    @Test
    void create_withValidMaintenance_writesNormalizedJson() {
        var params = new SiteCreateParams();
        params.setName("官网");
        params.setUrl("https://example.com");
        params.setMaintenance("{\"start\":\"22:00\",\"end\":\"08:00\",\"days\":[\"MON\",\"WED\",\"FRI\"]}");

        when(repo.existsByName("官网")).thenReturn(false);
        when(repo.existsByUrl("https://example.com")).thenReturn(false);
        when(categoryService.defaultCategoryId()).thenReturn(1L);
        when(repo.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDTO(any(Site.class))).thenAnswer(inv -> {
            var s = (Site) inv.getArgument(0);
            var dto = new SiteDTO();
            dto.setId(1L);
            dto.setName(s.getName());
            return dto;
        });

        service.create(params);

        // 捕获写入的 site,验证 maintenance 已写入(解析后语义一致)
        var captor = ArgumentCaptor.forClass(Site.class);
        verify(repo).save(captor.capture());
        var saved = captor.getValue();
        var w = saved.maintenanceWindow();
        assertNotNull(w);
        assertEquals("22:00", w.startTime().toString());
        assertEquals("08:00", w.endTime().toString());
        assertEquals(java.util.List.of("MON", "WED", "FRI"), w.days());
    }

    /// create 非法运维时段 JSON → 400 BAD_REQUEST。
    @Test
    void create_withInvalidMaintenance_throws400() {
        var params = new SiteCreateParams();
        params.setName("官网");
        params.setUrl("https://example.com");
        // start == end:语义非法
        params.setMaintenance("{\"start\":\"08:00\",\"end\":\"08:00\"}");

        when(repo.existsByName("官网")).thenReturn(false);
        when(repo.existsByUrl("https://example.com")).thenReturn(false);
        when(categoryService.defaultCategoryId()).thenReturn(1L);

        assertThrows(AppException.class, () -> service.create(params));
        verify(repo, never()).save(any(Site.class));
    }

    /// update PATCH 语义:未传 maintenance / unsetMaintenance → 不动当前值。
    @Test
    void update_maintenanceNotProvided_doesNotChangeExisting() {
        var site = new Site();
        site.setId(1L);
        site.setName("旧名");
        site.setUrl("https://old.example.com");
        site.setMaintenance("{\"start\":\"22:00\",\"end\":\"08:00\"}");
        when(repo.findById(1L)).thenReturn(Optional.of(site));
        when(repo.existsByNameAndIdNot("新名", 1L)).thenReturn(false);
        when(repo.existsByUrlAndIdNot("https://new.example.com", 1L)).thenReturn(false);
        when(repo.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDTO(any(Site.class))).thenAnswer(inv -> {
            var s = (Site) inv.getArgument(0);
            var dto = new SiteDTO();
            dto.setId(s.getId());
            dto.setName(s.getName());
            return dto;
        });

        var params = new SiteUpdateParams();
        params.setId(1L);
        params.setName("新名");
        params.setUrl("https://new.example.com");
        // 不传 maintenance,也不传 unsetMaintenance

        service.update(params);

        var captor = ArgumentCaptor.forClass(Site.class);
        verify(repo).save(captor.capture());
        var saved = captor.getValue();
        // 维护时段未变:仍能解析出原值
        var w = saved.maintenanceWindow();
        assertNotNull(w);
        assertEquals("22:00", w.startTime().toString());
        assertEquals("08:00", w.endTime().toString());
    }

    /// update unsetMaintenance=true:清空站点 maintenance 字段(= 启用 → 关闭)。
    @Test
    void update_unsetMaintenance_clearsField() {
        var site = new Site();
        site.setId(1L);
        site.setName("旧名");
        site.setUrl("https://old.example.com");
        site.setMaintenance("{\"start\":\"22:00\",\"end\":\"08:00\"}");
        when(repo.findById(1L)).thenReturn(Optional.of(site));
        when(repo.existsByNameAndIdNot("旧名", 1L)).thenReturn(false);
        when(repo.existsByUrlAndIdNot("https://old.example.com", 1L)).thenReturn(false);
        when(repo.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDTO(any(Site.class))).thenAnswer(inv -> {
            var s = (Site) inv.getArgument(0);
            var dto = new SiteDTO();
            dto.setId(s.getId());
            dto.setName(s.getName());
            return dto;
        });

        var params = new SiteUpdateParams();
        params.setId(1L);
        params.setName("旧名");
        params.setUrl("https://old.example.com");
        params.setUnsetMaintenance(true);

        service.update(params);

        var captor = ArgumentCaptor.forClass(Site.class);
        verify(repo).save(captor.capture());
        var saved = captor.getValue();
        // 已被清空:解析后为 NONE
        assertTrue(saved.maintenanceWindow().isEmpty());
    }
}