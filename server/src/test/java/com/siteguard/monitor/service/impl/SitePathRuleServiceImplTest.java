package com.siteguard.monitor.service.impl;

import com.siteguard.common.exception.AppException;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.detection.SiteCheckState;
import com.siteguard.monitor.alert.detection.SiteCheckStateId;
import com.siteguard.monitor.alert.detection.SiteCheckStateRepository;
import com.siteguard.monitor.dto.SitePathRuleDTO;
import com.siteguard.monitor.dto.SitePathRuleListRequest;
import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.mapper.SitePathRuleMapper;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.entity.Site;
import com.siteguard.site.repository.SiteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SitePathRuleServiceImplTest {

    @Mock SitePathRuleRepository ruleRepo;
    @Mock SiteRepository siteRepo;
    @Mock SitePathRuleMapper mapper;
    @Mock SiteCheckStateRepository stateRepo;
    @InjectMocks SitePathRuleServiceImpl service;

    @Test
    void listBySite_returnsDTOs() {
        var r = new SitePathRule();
        r.setId(1L);
        r.setSiteId(2L);
        r.setPath("/a");
        r.setExpectedHttpStatus(200);
        r.setLastHttpStatus(200);
        r.setLastErrorMessage(null);
        when(ruleRepo.findBySiteIdOrderByIdAsc(2L)).thenReturn(List.of(r));
        when(stateRepo.findByAlertKind(AlertKind.PATH_CHECK)).thenReturn(List.of());

        var dtos = service.listBySite(2L);

        assertEquals(1, dtos.size());
        assertEquals("/a", dtos.get(0).path());
        assertEquals(200, dtos.get(0).lastHttpStatus());
        assertNull(dtos.get(0).alertingSince());
    }

    @Test
    void listBySite_emptyList() {
        when(ruleRepo.findBySiteIdOrderByIdAsc(99L)).thenReturn(List.of());
        when(stateRepo.findByAlertKind(AlertKind.PATH_CHECK)).thenReturn(List.of());

        var dtos = service.listBySite(99L);

        assertTrue(dtos.isEmpty());
    }

    @Test
    void listBySite_fillsAlertingSinceFromState() {
        long siteId = 1L;
        var rule1 = new SitePathRule();
        rule1.setId(10L);
        rule1.setSiteId(siteId);
        rule1.setPath("/api/orders");
        rule1.setExpectedHttpStatus(200);
        rule1.setLastHttpStatus(500);
        rule1.setLastErrorMessage(null);
        rule1.setLastCheckedAt(1000L);

        var rule2 = new SitePathRule();
        rule2.setId(11L);
        rule2.setSiteId(siteId);
        rule2.setPath("/api/users");
        rule2.setExpectedHttpStatus(200);
        rule2.setLastHttpStatus(200);
        rule2.setLastErrorMessage(null);
        rule2.setLastCheckedAt(1000L);

        when(ruleRepo.findBySiteIdOrderByIdAsc(siteId)).thenReturn(List.of(rule1, rule2));

        var state1 = SiteCheckState.builder()
                .id(new SiteCheckStateId(siteId, "PATH_CHECK", "/api/orders"))
                .lastNotifiedAt(0L).updatedAt(5000L).build();
        when(stateRepo.findByAlertKind(AlertKind.PATH_CHECK)).thenReturn(List.of(state1));

        var result = service.listBySite(siteId);
        assertEquals(2, result.size());
        var orders = result.stream().filter(r -> "/api/orders".equals(r.path())).findFirst().orElseThrow();
        assertEquals(5000L, orders.alertingSince());
        var users = result.stream().filter(r -> "/api/users".equals(r.path())).findFirst().orElseThrow();
        assertNull(users.alertingSince());
    }

    @Test
    void set_siteNotFound_throws() {
        when(siteRepo.findById(99L)).thenReturn(Optional.empty());
        var req = new SitePathRuleListRequest(99L, List.of(
                new SitePathRuleDTO(null, 99L, "/a", 200, null, null, null, null)
        ));

        assertThrows(AppException.class, () -> service.set(req));
    }

    @Test
    void set_deletesOldAndInsertsNew_idInDtoIsIgnored() {
        // 即便前端在请求里把已存在规则的 id 原样回传，后端也必须把它清掉：
        // 否则 saveAll 触发 merge()，但行已经被批量 DELETE 删过 → StaleObjectStateException。
        when(siteRepo.findById(1L)).thenReturn(Optional.of(siteWithId1L()));
        when(ruleRepo.deleteBySiteId(1L)).thenReturn(3L);
        when(mapper.toEntity(any(SitePathRuleDTO.class))).thenAnswer(inv -> {
            var dto = inv.getArgument(0, SitePathRuleDTO.class);
            var e = new SitePathRule();
            e.setId(dto.id());
            e.setSiteId(dto.siteId());
            e.setPath(dto.path());
            e.setExpectedHttpStatus(dto.expectedHttpStatus());
            return e;
        });

        var req = new SitePathRuleListRequest(1L, List.of(
                new SitePathRuleDTO(10L, 1L, "/a", 200, null, null, null, null),
                new SitePathRuleDTO(11L, 1L, "/b", 404, null, null, null, null)
        ));

        service.set(req);

        verify(ruleRepo).deleteBySiteId(1L);
        ArgumentCaptor<List<SitePathRule>> captor = ArgumentCaptor.forClass(List.class);
        verify(ruleRepo).saveAll(captor.capture());
        var saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals("/a", saved.get(0).getPath());
        assertEquals("/b", saved.get(1).getPath());
        // 全删全插语义：id 由数据库分配，请求体里的 id 一律强制清空
        assertEquals(null, saved.get(0).getId());
        assertEquals(null, saved.get(1).getId());
        // 新增规则的 last_* 必须为 null（不允许前端伪造探测结果）
        assertEquals(null, saved.get(0).getLastHttpStatus());
        assertEquals(null, saved.get(0).getLastCheckedAt());
    }

    @Test
    void setClearsConsecutiveFailuresCounterOnRuleInsert() {
        // 编辑已有规则时：即使 mapper 把旧的 consecutiveFailures 一起回带（前端不该传，但 mapper 行为是
        // 机械映射），service 也必须强制归零。理由：expected_http_status 可能已经变了，
        // 旧 counter 是基于"旧 expected_http_status + 旧 last_http_status"统计出来的，
        // 直接套到新规则上没有意义，留着会误判。
        var siteId = 1L;
        when(siteRepo.findById(siteId)).thenReturn(Optional.of(siteWithId1L()));
        when(ruleRepo.deleteBySiteId(siteId)).thenReturn(0L);
        when(mapper.toEntity(any(SitePathRuleDTO.class))).thenAnswer(inv -> {
            var dto = inv.getArgument(0, SitePathRuleDTO.class);
            var e = new SitePathRule();
            e.setId(dto.id());
            e.setSiteId(dto.siteId());
            e.setPath(dto.path());
            e.setExpectedHttpStatus(dto.expectedHttpStatus());
            e.setLastCheckedAt(dto.lastCheckedAt());
            e.setLastHttpStatus(dto.lastHttpStatus());
            e.setLastErrorMessage(dto.lastErrorMessage());
            e.setConsecutiveFailures(99);  // 模拟 mapper 把旧 counter 一起带回来
            return e;
        });

        var req = new SitePathRuleListRequest(siteId, List.of(
                new SitePathRuleDTO(null, siteId, "/api/v2", 200, null, null, null, null)
        ));

        service.set(req);

        ArgumentCaptor<List<SitePathRule>> captor = ArgumentCaptor.forClass(List.class);
        verify(ruleRepo).saveAll(captor.capture());
        var saved = captor.getValue();
        assertEquals(1, saved.size());
        // 探测状态字段全部清空
        assertNull(saved.get(0).getLastCheckedAt());
        assertNull(saved.get(0).getLastHttpStatus());
        assertNull(saved.get(0).getLastErrorMessage());
        // counter 一并归零（这是本测试要保护的契约）
        assertEquals(0, saved.get(0).getConsecutiveFailures());
    }

    @Test
    void set_emptyList_clearsAllRules() {
        when(siteRepo.findById(1L)).thenReturn(Optional.of(siteWithId1L()));
        when(ruleRepo.deleteBySiteId(1L)).thenReturn(2L);

        var req = new SitePathRuleListRequest(1L, List.of());

        service.set(req);

        verify(ruleRepo).deleteBySiteId(1L);
        verify(ruleRepo, never()).saveAll(any());
    }

    @Test
    void deleteById_notFound_isNoop() {
        when(ruleRepo.existsById(999L)).thenReturn(false);

        service.delete(999L);

        verify(ruleRepo, never()).deleteById(anyLong());
    }

    @Test
    void deleteById_exists_deletes() {
        when(ruleRepo.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(ruleRepo).deleteById(1L);
    }

    private Site siteWithId1L() {
        var s = new Site();
        s.setId(1L);
        s.setName("test");
        s.setUrl("https://example.com");
        return s;
    }
}