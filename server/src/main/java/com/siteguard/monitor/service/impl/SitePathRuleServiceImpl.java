package com.siteguard.monitor.service.impl;

import com.siteguard.common.exception.Errors;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.detection.SiteCheckState;
import com.siteguard.monitor.alert.detection.SiteCheckStateRepository;
import com.siteguard.monitor.dto.SitePathRuleDTO;
import com.siteguard.monitor.dto.SitePathRuleListRequest;
import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.mapper.SitePathRuleMapper;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.monitor.service.SitePathRuleService;
import com.siteguard.site.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// 站点子路由规则 service。
///
/// 设计要点：
/// - 整批 set 走"全删 + 全插"语义，不做 diff；前端在请求体里只带"用户期望保留"的行
/// - 前端不能伪造 last_* 字段：set 时强制把探测状态字段置 null
/// - 前端不能伪造 id：set 时强制把 entity.id 置 null，让 JPA 走 persist（DB 自增分配 id），
///   避免 deleteBySiteId 之后 merge 抛 StaleObjectStateException
/// - listBySite 联查 site_check_state（PATH_CHECK），把每条规则对应的 alertingSince 一并填充：
///   非 null 表示当前在告警，值即 state.updatedAt（lastNotifiedAt + updatedAt 同一时刻）
@Service
@RequiredArgsConstructor
public class SitePathRuleServiceImpl implements SitePathRuleService {

    private final SitePathRuleRepository ruleRepo;
    private final SiteRepository siteRepo;
    private final SitePathRuleMapper mapper;
    private final SiteCheckStateRepository stateRepo;

    @Override
    @Transactional(readOnly = true)
    public List<SitePathRuleDTO> listBySite(Long siteId) {
        List<SitePathRule> rules = ruleRepo.findBySiteIdOrderByIdAsc(siteId);
        // 一次性拉取全部 PATH_CHECK state，在内存里按 (siteId, path) 过滤，构造 path -> alertingSince 的查找表
        Map<String, Long> alertingSince = new HashMap<>();
        for (SiteCheckState s : stateRepo.findByAlertKind(AlertKind.PATH_CHECK)) {
            if (siteId.equals(s.getId().siteId())) {
                alertingSince.put(s.getId().bucket(), s.getUpdatedAt());
            }
        }
        return rules.stream()
                .map(r -> toDto(r, alertingSince.get(r.getPath())))
                .toList();
    }

    /// 组装单条 DTO。alertingSince=null 表示该路径当前不在告警。
    private SitePathRuleDTO toDto(SitePathRule r, Long alertingSince) {
        return new SitePathRuleDTO(
                r.getId(), r.getSiteId(), r.getPath(), r.getExpectedHttpStatus(),
                r.getLastCheckedAt(), r.getLastHttpStatus(), r.getLastErrorMessage(),
                alertingSince);
    }

    @Override
    @Transactional
    public void set(SitePathRuleListRequest request) {
        // 站点不存在直接 404
        siteRepo.findById(request.siteId())
                .orElseThrow(() -> Errors.NOT_FOUND.toException("站点不存在 (ID: {})", request.siteId()));
        // 全删旧规则（无论是否为空都调用，幂等）
        ruleRepo.deleteBySiteId(request.siteId());
        // 全插新规则；空列表 = 清空该站点全部规则
        if (request.rules().isEmpty()) {
            return;
        }
        var entities = request.rules().stream()
                .map(dto -> {
                    var e = mapper.toEntity(dto);
                    // id 强制为 null，由数据库自增分配；否则走 merge 时会因行已被批量删除而抛
                    // StaleObjectStateException（前端在编辑已有规则时会把原 id 一并发回，必须忽略）
                    e.setId(null);
                    // 探测状态字段强制为 null，等待下次探测写入；防止前端伪造历史探测结果
                    e.setLastCheckedAt(null);
                    e.setLastHttpStatus(null);
                    e.setLastErrorMessage(null);
                    // counter 一并归零：expected_http_status 可能已被前端改过，
                    // 旧 counter 是基于"旧 expected + 旧 last_http_status"统计的，留着会误判
                    e.setConsecutiveFailures(0);
                    return e;
                })
                .toList();
        ruleRepo.saveAll(entities);
    }

    @Override
    @Transactional
    public void delete(Long ruleId) {
        if (!ruleRepo.existsById(ruleId)) {
            return;  // 幂等：不存在不抛
        }
        ruleRepo.deleteById(ruleId);
    }
}