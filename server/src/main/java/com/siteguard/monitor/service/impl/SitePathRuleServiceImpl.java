package com.siteguard.monitor.service.impl;

import com.siteguard.common.exception.Errors;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.detection.SiteCheckState;
import com.siteguard.monitor.alert.detection.SiteCheckStateRepository;
import com.siteguard.monitor.dto.SitePathCheckHistoryDTO;
import com.siteguard.monitor.dto.SitePathRuleDTO;
import com.siteguard.monitor.dto.SitePathRuleListRequest;
import com.siteguard.monitor.dto.SitePathRuleTestRequest;
import com.siteguard.monitor.dto.SitePathRuleTestResultDTO;
import com.siteguard.monitor.entity.SitePathCheckHistory;
import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.jsoncondition.JsonAssertionConfigCodec;
import com.siteguard.monitor.jsoncondition.JsonAssertionConfigDTO;
import com.siteguard.monitor.mapper.SitePathRuleMapper;
import com.siteguard.monitor.probe.PathCheckProbe;
import com.siteguard.monitor.probe.PathCheckType;
import com.siteguard.monitor.repository.SitePathCheckHistoryRepository;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.monitor.service.SitePathRuleService;
import com.siteguard.site.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// 站点子路由规则 service。整批保存采用“先校验和构造，再全删全插”，
/// 防止复杂 JSON 配置非法时提前删除用户已有规则。
@Service
@RequiredArgsConstructor
public class SitePathRuleServiceImpl implements SitePathRuleService {

    private static final int MAX_RECENT_HISTORY = 30;

    private final SitePathRuleRepository ruleRepo;
    private final SiteRepository siteRepo;
    private final SitePathRuleMapper mapper;
    private final SiteCheckStateRepository stateRepo;
    private final SitePathCheckHistoryRepository historyRepo;
    private final JsonAssertionConfigCodec assertionCodec;
    private final PathCheckProbe pathCheckProbe;

    @Override
    @Transactional(readOnly = true)
    public List<SitePathRuleDTO> listBySite(Long siteId) {
        List<SitePathRule> rules = ruleRepo.findBySiteIdOrderByIdAsc(siteId);
        Map<String, Long> alertingSince = new HashMap<>();
        for (SiteCheckState state : stateRepo.findByAlertKind(AlertKind.PATH_CHECK)) {
            if (siteId.equals(state.getId().siteId())) {
                alertingSince.put(state.getId().bucket(), state.getUpdatedAt());
            }
        }
        return rules.stream()
                .map(rule -> toDto(rule, alertingSince.get(rule.getPath())))
                .toList();
    }

    private SitePathRuleDTO toDto(SitePathRule rule, Long alertingSince) {
        PathCheckType checkType = normalizeCheckType(rule.getCheckType());
        JsonAssertionConfigDTO assertionConfig = null;
        String jsonDetail = rule.getLastJsonDetail();
        if (rule.getAssertionConfig() != null) {
            try {
                assertionConfig = assertionCodec.decode(rule.getAssertionConfig());
            } catch (IllegalArgumentException e) {
                /// 单条存量脏配置不能阻断整个站点的规则列表；把问题作为只读诊断返回。
                jsonDetail = "JSON 条件配置不可读取：" + e.getMessage();
            }
        }
        return new SitePathRuleDTO(
                rule.getId(), rule.getSiteId(), rule.getPath(), rule.getExpectedHttpStatus(),
                checkType, rule.getExpectedText(), assertionConfig,
                rule.getLastCheckedAt(), rule.getLastHttpStatus(), rule.getLastTextMatched(),
                rule.getLastJsonMatched(), jsonDetail, rule.getLastErrorMessage(), alertingSince);
    }

    @Override
    @Transactional
    public void set(SitePathRuleListRequest request) {
        siteRepo.findById(request.siteId())
                .orElseThrow(() -> Errors.NOT_FOUND.toException("站点不存在 (ID: {})", request.siteId()));

        /// 在 DELETE 前完成全部校验和 entity 构造；虽然事务异常可回滚，先校验仍能避免 flush 时序
        /// 或未来事务边界变化造成“非法请求清空旧规则”的隐患。
        List<SitePathRule> entities = request.rules().stream()
                .map(this::validateAndBuildEntity)
                .toList();

        ruleRepo.deleteBySiteId(request.siteId());
        if (!entities.isEmpty()) {
            ruleRepo.saveAll(entities);
        }
    }

    private SitePathRule validateAndBuildEntity(SitePathRuleDTO dto) {
        PathCheckType checkType = normalizeCheckType(dto.checkType());
        if (dto.path() == null || dto.path().isBlank() || !dto.path().startsWith("/") || dto.path().startsWith("//")) {
            throw Errors.BAD_REQUEST.toException("子路由 path 必须是单斜杠开头的相对路径 (path=%s)", dto.path());
        }
        if (dto.expectedHttpStatus() == null) {
            throw Errors.BAD_REQUEST.toException("expectedHttpStatus 不能为空 (path=%s)", dto.path());
        }
        if (checkType == PathCheckType.KEYWORD
                && (dto.expectedText() == null || dto.expectedText().trim().isEmpty())) {
            throw Errors.BAD_REQUEST.toException("关键字模式下 expectedText 不能为空 (path=%s)", dto.path());
        }

        String encodedAssertion = null;
        if (checkType == PathCheckType.JSON_ASSERT) {
            try {
                encodedAssertion = assertionCodec.encode(dto.assertionConfig());
            } catch (IllegalArgumentException e) {
                throw Errors.BAD_REQUEST.toException(e, "JSON 条件配置不合法 (path=%s): %s", dto.path(), e.getMessage());
            }
        }

        var entity = mapper.toEntity(dto);
        entity.setId(null);
        entity.setCheckType(checkType);
        entity.setAssertionConfig(encodedAssertion);
        entity.setLastCheckedAt(null);
        entity.setLastHttpStatus(null);
        entity.setLastTextMatched(null);
        entity.setLastJsonMatched(null);
        entity.setLastJsonDetail(null);
        entity.setLastErrorMessage(null);
        entity.setConsecutiveFailures(0);
        return entity;
    }

    private static PathCheckType normalizeCheckType(PathCheckType checkType) {
        return checkType == null ? PathCheckType.HTTP_STATUS : checkType;
    }

    @Override
    public SitePathRuleTestResultDTO test(Long siteId, SitePathRuleTestRequest request) {
        var site = siteRepo.findById(siteId)
                .orElseThrow(() -> Errors.NOT_FOUND.toException("站点不存在 (ID: {})", siteId));
        var dto = new SitePathRuleDTO(null, siteId, request.path(), request.expectedHttpStatus(),
                request.checkType(), request.expectedText(), request.assertionConfig(),
                null, null, null, null, null, null, null);
        SitePathRule rule = validateAndBuildEntity(dto);
        rule.setSiteId(siteId);
        var result = pathCheckProbe.test(site, rule);
        return new SitePathRuleTestResultDTO(
                result.requestCompleted(), result.httpStatus(), result.httpStatusMatched(),
                result.bodyParsed(), result.jsonMatched(), result.textMatched(), result.healthy(),
                result.summary(), result.conditions(), result.errorMessage());
    }

    @Override
    @Transactional
    public void delete(Long ruleId) {
        if (ruleRepo.existsById(ruleId)) {
            ruleRepo.deleteById(ruleId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SitePathCheckHistoryDTO> listRecentHistory(Long ruleId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_RECENT_HISTORY));
        var pageable = PageRequest.of(0, safeLimit);
        return historyRepo.findByRuleIdOrderByCheckedAtDesc(ruleId, pageable).stream()
                .map(SitePathRuleServiceImpl::toHistoryDto)
                .toList();
    }

    private static SitePathCheckHistoryDTO toHistoryDto(SitePathCheckHistory history) {
        return new SitePathCheckHistoryDTO(
                history.getId(), history.getSiteId(), history.getRuleId(), history.getPath(),
                history.getCheckedAt(), history.getStatus(), history.getHttpStatus(),
                history.getTextMatched(), history.getJsonMatched(), history.getJsonDetail(),
                history.getErrorMessage());
    }
}
