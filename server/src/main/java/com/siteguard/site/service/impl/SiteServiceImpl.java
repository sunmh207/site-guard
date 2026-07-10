package com.siteguard.site.service.impl;

import com.siteguard.category.service.CategoryService;
import com.siteguard.common.exception.Errors;
import com.siteguard.monitor.repository.SiteCheckHistoryRepository;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.dto.SiteCreateParams;
import com.siteguard.site.dto.SiteDTO;
import com.siteguard.site.dto.SiteSearchParams;
import com.siteguard.site.dto.SiteUpdateParams;
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