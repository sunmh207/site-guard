package com.siteguard.site.service.impl;

import com.siteguard.category.service.CategoryService;
import com.siteguard.common.exception.Errors;
import com.siteguard.monitor.probe.CertForgive;
import com.siteguard.monitor.probe.CertForgiveType;
import com.siteguard.monitor.repository.SiteCheckHistoryRepository;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.dto.SiteCreateParams;
import com.siteguard.site.dto.SiteDTO;
import com.siteguard.site.dto.SiteSearchParams;
import com.siteguard.site.dto.SiteUpdateParams;
import com.siteguard.site.entity.MaintenanceWindow;
import com.siteguard.site.entity.QSite;
import com.siteguard.site.entity.Site;
import com.siteguard.site.entity.SiteStatus;
import com.siteguard.site.mapper.SiteMapper;
import com.siteguard.site.repository.SiteRepository;
import com.siteguard.site.service.SiteService;
import com.querydsl.core.BooleanBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/// 站点业务服务实现
///
/// - create / update：写入前调用 existsByXxx 做唯一性校验，冲突时抛 CONFLICT。
/// - update / delete / getDetail：读前调用 findById，不存在时抛 NOT_FOUND。
/// - search：根据 keyword 模糊匹配 name 或 url，根据 availabilityStatus 精确匹配，组合 Querydsl Predicate。
/// - moveSites：拖拽落点时按 ID 列表批量改 categoryId。
@Service
@RequiredArgsConstructor
@Slf4j
public class SiteServiceImpl implements SiteService {

    private final SiteRepository repo;
    private final SiteMapper mapper;
    private final SiteCheckHistoryRepository historyRepo;
    private final SitePathRuleRepository pathRuleRepo;
    private final CategoryService categoryService;

    @Override
    public SiteDTO create(SiteCreateParams params) {
        if (repo.existsByName(params.getName())) {
            throw Errors.CONFLICT.toException("站点名称已存在");
        }
        if (repo.existsByUrl(params.getUrl())) {
            throw Errors.CONFLICT.toException("站点 URL 已被监控");
        }
        // 分类：未指定则落入系统默认分类，保证 site.category_id 必填约束
        Long categoryId = params.getCategoryId() != null
                ? params.getCategoryId()
                : categoryService.defaultCategoryId();

        var site = new Site();
        site.setName(params.getName());
        site.setUrl(params.getUrl());
        site.setCategoryId(categoryId);
        // 创建时 status 默认为 UNKNOWN，监控字段全部留空，等待探测任务填充
        site.setAvailabilityStatus(SiteStatus.UNKNOWN);
        // 站点级连续失败阈值覆盖；null 表示沿用全局默认
        site.setConsecutiveFailuresBeforeAlert(params.getConsecutiveFailuresBeforeAlert());
        // 证书校验分级放行：把 3 个开关拼成 JSON 数组
        site.setCertForgive(resolveCertForgiveJson(params.getCertForgiveChainIncomplete(),
                params.getCertForgiveDomainMismatch(), params.getCertForgiveSelfSigned()));
        // 运维时段:校验后写入(非法 JSON / 语义非法 → 400)
        site.setMaintenance(resolveMaintenanceJson(params.getMaintenance()));
        repo.save(site);
        return mapper.toDTO(site);
    }

    @Override
    public SiteDTO update(SiteUpdateParams params) {
        var site = repo.findById(params.getId())
                .orElseThrow(() -> Errors.NOT_FOUND.toException("站点不存在 (ID: {})", params.getId()));

        // 排除自身后判断 name / url 是否冲突
        if (repo.existsByNameAndIdNot(params.getName(), params.getId())) {
            throw Errors.CONFLICT.toException("站点名称已存在");
        }
        if (repo.existsByUrlAndIdNot(params.getUrl(), params.getId())) {
            throw Errors.CONFLICT.toException("站点 URL 已被监控");
        }

        site.setName(params.getName());
        site.setUrl(params.getUrl());
        // categoryId 为 null 表示不改（PATCH 语义）
        if (params.getCategoryId() != null) {
            site.setCategoryId(params.getCategoryId());
        }
        // 站点级连续失败阈值覆盖；null 表示沿用全局默认（前端编辑表单可主动清空字段）
        site.setConsecutiveFailuresBeforeAlert(params.getConsecutiveFailuresBeforeAlert());
        // 证书校验分级放行：只要任意一个开关字段非 null 就整体重建；全 null = 不改当前集合
        if (params.getCertForgiveChainIncomplete() != null
                || params.getCertForgiveDomainMismatch() != null
                || params.getCertForgiveSelfSigned() != null) {
            site.setCertForgive(resolveCertForgiveJson(params.getCertForgiveChainIncomplete(),
                    params.getCertForgiveDomainMismatch(), params.getCertForgiveSelfSigned()));
        }
        // 运维时段 PATCH 语义:unsetMaintenance=true → 清空;maintenance 非空 → 校验后写入;否则不动
        if (Boolean.TRUE.equals(params.getUnsetMaintenance())) {
            site.setMaintenance(null);
        } else if (params.getMaintenance() != null) {
            site.setMaintenance(resolveMaintenanceJson(params.getMaintenance()));
        }
        repo.save(site);
        return mapper.toDTO(site);
    }

    @Override
    public void delete(Long id) {
        var site = repo.findById(id)
                .orElseThrow(() -> Errors.NOT_FOUND.toException("站点不存在 (ID: {})", id));
        // 先清理探测历史（不阻塞主操作：清理失败不抛）
        try {
            historyRepo.deleteBySiteId(id);
        } catch (RuntimeException e) {
            // 历史清理失败不阻塞主流程
        }
        // 清理子路由规则（无外键 cascade，service 层显式清理）
        try {
            pathRuleRepo.deleteBySiteId(id);
        } catch (RuntimeException e) {
            // 规则清理失败不阻塞主流程，但留 warn 日志便于排障孤儿 site_path_rule 行
            log.warn("清理站点 {} 的 site_path_rule 失败: {}", id, e.getMessage());
        }
        repo.delete(site);
    }

    @Override
    public SiteDTO getDetail(Long id) {
        var site = repo.findById(id)
                .orElseThrow(() -> Errors.NOT_FOUND.toException("站点不存在 (ID: {})", id));
        return mapper.toDTO(site);
    }

    /// 把前端传来的 3 个开关（可为 null = 不参与）拼成 site.cert_forgive JSON 字符串数组。
    /// 仅当 Boolean.TRUE 时纳入枚举值；null / false 均视为"不放"。
    private String resolveCertForgiveJson(Boolean chain, Boolean domain, Boolean selfSigned) {
        EnumSet<CertForgiveType> types = EnumSet.noneOf(CertForgiveType.class);
        if (Boolean.TRUE.equals(chain))      types.add(CertForgiveType.CHAIN_INCOMPLETE);
        if (Boolean.TRUE.equals(domain))     types.add(CertForgiveType.DOMAIN_MISMATCH);
        if (Boolean.TRUE.equals(selfSigned)) types.add(CertForgiveType.SELF_SIGNED);
        return CertForgive.json(types);
    }

    /// 校验前端传来的 maintenance JSON,通过后返回规范化后的 JSON 字符串用于入库。
    /// 校验失败抛 Errors.BAD_REQUEST,对齐 SiteController 的 AppException 处理。
    ///
    /// 空字符串 = 关闭(返回 null,让列保持 NULL);
    /// 非空字符串必须能解析为合法结构:JSON 对象;start / end 格式 "HH:mm" 且不相等;
    /// days(若有)是 MON..SUN 子集。
    private String resolveMaintenanceJson(String maintenance) {
        if (maintenance == null) {
            return null;
        }
        // 前端取消启用时传空字符串,关闭(让列保持 NULL)
        if (maintenance.isBlank()) {
            return null;
        }
        // 先整体 parse;失败(非法 JSON / 非法 days / start==end 等) → MaintenanceWindow.NONE
        MaintenanceWindow w = MaintenanceWindow.parse(maintenance);
        if (w.isEmpty()) {
            // 进一步区分:是"用户本意就是关闭"还是"输入错误 → 应报错"
            // 约定:若原始串本就是空 JSON `{}` 当关闭处理(null);其它任何非法输入 → 400
            boolean intentionalEmpty = maintenance.replaceAll("\\s", "").equals("{}");
            if (intentionalEmpty) {
                return null;
            }
            throw Errors.BAD_REQUEST.toException(
                    "maintenance 格式非法,期望 JSON 对象,例 {\"start\":\"22:00\",\"end\":\"08:00\"}");
        }
        // 重新序列化一次,得到规范化表示(去掉前端可能缺失的 days 默认排序、去重等)
        String normalized = MaintenanceWindow.json(w);
        if (normalized == null) {
            throw Errors.BAD_REQUEST.toException("maintenance 序列化失败");
        }
        return normalized;
    }

    @Override
    public SiteDTO setPaused(Long id, boolean paused) {
        var site = repo.findById(id)
                .orElseThrow(() -> Errors.NOT_FOUND.toException("站点不存在 (ID: {})", id));
        site.setPaused(paused);
        repo.save(site);
        return mapper.toDTO(site);
    }

    @Override
    public Page<@NonNull SiteDTO> search(SiteSearchParams params, Pageable pager) {
        var q = QSite.site;
        var b = new BooleanBuilder();

        // 关键字：模糊匹配 name 或 url
        if (StringUtils.isNotEmpty(params.getKeyword())) {
            b.and(q.name.contains(params.getKeyword()).or(q.url.contains(params.getKeyword())));
        }
        // 状态：精确匹配
        if (params.getAvailabilityStatus() != null) {
            b.and(q.availabilityStatus.eq(params.getAvailabilityStatus()));
        }
        // 分类：自动包含该分类及其所有后代，前端"点击左侧分类节点"即可看子树下所有站点
        if (params.getCategoryId() != null) {
            Set<Long> ids = categoryService.descendantIds(params.getCategoryId());
            b.and(q.categoryId.in(ids));
        }

        var sites = repo.findAll(b, pager);
        var rows = mapper.toRows(sites.toList());
        return new PageImpl<>(rows, pager, sites.getTotalElements());
    }

    /// 批量把站点迁到目标分类：直接走 Repository 单条 UPDATE，避免 N 次 save。
    /// 返回受影响行数（可能为 0，例如所有 siteId 都已被删除）。
    @Override
    public int moveSites(List<Long> siteIds, Long categoryId) {
        return repo.updateCategoryIdBulkByIds(siteIds, categoryId);
    }
}