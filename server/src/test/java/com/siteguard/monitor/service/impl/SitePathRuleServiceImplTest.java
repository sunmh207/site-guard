package com.siteguard.monitor.service.impl;

import com.siteguard.common.exception.AppException;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.detection.SiteCheckState;
import com.siteguard.monitor.alert.detection.SiteCheckStateId;
import com.siteguard.monitor.alert.detection.SiteCheckStateRepository;
import com.siteguard.monitor.dto.SitePathCheckHistoryDTO;
import com.siteguard.monitor.dto.SitePathRuleDTO;
import com.siteguard.monitor.dto.SitePathRuleListRequest;
import com.siteguard.monitor.entity.CheckStatus;
import com.siteguard.monitor.entity.SitePathCheckHistory;
import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.probe.PathCheckType;
import com.siteguard.monitor.mapper.SitePathRuleMapper;
import com.siteguard.monitor.repository.SitePathCheckHistoryRepository;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.entity.Site;
import com.siteguard.site.repository.SiteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SitePathRuleServiceImplTest {

    @Mock SitePathRuleRepository ruleRepo;
    @Mock SiteRepository siteRepo;
    @Mock SitePathRuleMapper mapper;
    @Mock SiteCheckStateRepository stateRepo;
    @Mock SitePathCheckHistoryRepository historyRepo;
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
                new SitePathRuleDTO(null, 99L, "/a", 200, null, null, null, null, null, null, null)
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
                new SitePathRuleDTO(10L, 1L, "/a", 200, null, null, null, null, null, null, null),
                new SitePathRuleDTO(11L, 1L, "/b", 404, null, null, null, null, null, null, null)
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
                new SitePathRuleDTO(null, siteId, "/api/v2", 200, null, null, null, null, null, null, null)
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

    /// 构造测试用 DTO。字段顺序与 record 签名一致：
    /// (id, siteId, path, expectedHttpStatus, checkType, expectedText,
    ///  lastCheckedAt, lastHttpStatus, lastTextMatched, lastErrorMessage, alertingSince)
    private SitePathRuleDTO dto(Long id, String path, Integer expectedHttpStatus,
                                PathCheckType checkType, String expectedText) {
        return new SitePathRuleDTO(id, 1L, path, expectedHttpStatus, checkType, expectedText,
                null, null, null, null, null);
    }

    @Test
    void set_keywordRuleRequiresExpectedText() {
        // KEYWORD 模式 + expectedText 为空 → 400
        var bad = dto(null, "/ok", 200, PathCheckType.KEYWORD, "  ");
        when(siteRepo.findById(1L)).thenReturn(Optional.of(siteWithId1L()));

        assertThrows(AppException.class,
                () -> service.set(new SitePathRuleListRequest(1L, List.of(bad))));
        verify(ruleRepo, never()).saveAll(any());
    }

    @Test
    void set_httpStatusRuleRequiresExpectedHttpStatus() {
        // HTTP_STATUS 模式 + expectedHttpStatus 为空 → 400
        var bad = dto(null, "/ok", null, PathCheckType.HTTP_STATUS, null);
        when(siteRepo.findById(1L)).thenReturn(Optional.of(siteWithId1L()));

        assertThrows(AppException.class,
                () -> service.set(new SitePathRuleListRequest(1L, List.of(bad))));
        verify(ruleRepo, never()).saveAll(any());
    }

    @Test
    void set_keywordRuleForcesLastTextMatchedNull() {
        // KEYWORD 规则 set 后 last_text_matched 必须被置 null（防伪造）
        var ok = dto(null, "/ok", 200, PathCheckType.KEYWORD, "SiteGuard");
        when(siteRepo.findById(1L)).thenReturn(Optional.of(siteWithId1L()));
        when(mapper.toEntity(any())).thenAnswer(inv -> {
            SitePathRuleDTO d = inv.getArgument(0);
            var e = new SitePathRule();
            e.setId(d.id());
            e.setSiteId(d.siteId());
            e.setPath(d.path());
            e.setExpectedHttpStatus(d.expectedHttpStatus());
            e.setCheckType(d.checkType());
            e.setExpectedText(d.expectedText());
            e.setLastTextMatched(true); // 模拟前端伪造
            return e;
        });

        service.set(new SitePathRuleListRequest(1L, List.of(ok)));

        ArgumentCaptor<List<SitePathRule>> captor = ArgumentCaptor.forClass(List.class);
        verify(ruleRepo).saveAll(captor.capture());
        assertNull(captor.getValue().get(0).getLastTextMatched(),
                "last_text_matched 必须被强制置 null");
    }

    @Test
    void listRecentHistory_returnsMappedDTOs() {
        long ruleId = 42L;
        var h1 = new SitePathCheckHistory();
        h1.setId(1L);
        h1.setSiteId(1L);
        h1.setRuleId(ruleId);
        h1.setPath("/api");
        h1.setCheckedAt(2000L);
        h1.setStatus(CheckStatus.UP);
        h1.setHttpStatus(200);
        h1.setTextMatched(null);
        h1.setErrorMessage(null);
        var h2 = new SitePathCheckHistory();
        h2.setId(2L);
        h2.setSiteId(1L);
        h2.setRuleId(ruleId);
        h2.setPath("/api");
        h2.setCheckedAt(1000L);
        h2.setStatus(CheckStatus.ERROR);
        h2.setHttpStatus(null);
        h2.setTextMatched(null);
        h2.setErrorMessage("timeout");

        when(historyRepo.findByRuleIdOrderByCheckedAtDesc(eq(ruleId), any(Pageable.class)))
                .thenReturn(List.of(h1, h2));

        var result = service.listRecentHistory(ruleId, 30);

        assertEquals(2, result.size());
        // 第一条：UP + httpStatus=200
        assertEquals(CheckStatus.UP, result.get(0).status());
        assertEquals(200, result.get(0).httpStatus());
        // 第二条：ERROR + errorMessage
        assertEquals(CheckStatus.ERROR, result.get(1).status());
        assertEquals("timeout", result.get(1).errorMessage());
        // 验证 limit 传入（30 在硬上限内，直接透传）
        verify(historyRepo).findByRuleIdOrderByCheckedAtDesc(eq(ruleId), eq(PageRequest.of(0, 30)));
    }

    @Test
    void listRecentHistory_clampsLimitToMax() {
        long ruleId = 42L;
        when(historyRepo.findByRuleIdOrderByCheckedAtDesc(eq(ruleId), any(Pageable.class)))
                .thenReturn(List.of());

        service.listRecentHistory(ruleId, 999);

        // 超过硬上限 30 → 钳制到 30
        verify(historyRepo).findByRuleIdOrderByCheckedAtDesc(eq(ruleId), eq(PageRequest.of(0, 30)));
    }

    @Test
    void listRecentHistory_clampsNegativeLimitToOne() {
        long ruleId = 42L;
        when(historyRepo.findByRuleIdOrderByCheckedAtDesc(eq(ruleId), any(Pageable.class)))
                .thenReturn(List.of());

        service.listRecentHistory(ruleId, -5);

        // 负数 → 钳制到 1
        verify(historyRepo).findByRuleIdOrderByCheckedAtDesc(eq(ruleId), eq(PageRequest.of(0, 1)));
    }

    private Site siteWithId1L() {
        var s = new Site();
        s.setId(1L);
        s.setName("test");
        s.setUrl("https://example.com");
        return s;
    }
}