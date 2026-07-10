package com.siteguard.site.service;

import com.siteguard.site.dto.SiteCreateParams;
import com.siteguard.site.dto.SiteDTO;
import com.siteguard.site.dto.SiteSearchParams;
import com.siteguard.site.dto.SiteUpdateParams;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/// 站点业务服务
///
/// 负责站点 CRUD、唯一性校验、状态判定的业务逻辑。
/// Controller 不直接持有 Repository，所有数据访问都由本服务封装。
public interface SiteService {

    /// 创建站点。name 或 url 已存在时抛 AppException (CONFLICT)。
    SiteDTO create(SiteCreateParams params);

    /// 更新站点。不存在时抛 AppException (NOT_FOUND)，name 或 url 与他人冲突时抛 (CONFLICT)。
    SiteDTO update(SiteUpdateParams params);

    /// 删除站点。不存在时抛 AppException (NOT_FOUND)。
    void delete(Long id);

    /// 获取站点详情。不存在时抛 AppException (NOT_FOUND)。
    SiteDTO getDetail(Long id);

    /// 按 [SiteSearchParams] 条件过滤并分页查询。
    Page<@NonNull SiteDTO> search(SiteSearchParams params, Pageable pager);

    /// 设置站点暂停状态。不存在时抛 AppException (NOT_FOUND)。幂等。
    SiteDTO setPaused(Long id, boolean paused);

    /// 批量把站点移到目标分类
    int moveSites(List<Long> siteIds, Long categoryId);
}