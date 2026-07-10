package com.siteguard.monitor.repository;

import com.siteguard.monitor.entity.SitePathRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SitePathRuleRepository extends JpaRepository<SitePathRule, Long> {

    /// 拉取某站点的全部规则，按 id 升序。
    /// SiteCheckJob 中 PathCheckProbe 用；PathCheckAlertDefinition 中 eval 用。
    @Query("SELECT r FROM SitePathRule r WHERE r.siteId = :siteId ORDER BY r.id ASC")
    List<SitePathRule> findBySiteIdOrderByIdAsc(@Param("siteId") Long siteId);

    /// 按 (site_id, path) 查唯一一条规则。
    /// AlertDetectionService 在 PATH_CHECK 恢复时反查 expected_http_status，
    /// 把目标状态码拼到 IM 恢复消息中；rule 已删则 Optional 为空，恢复消息降级不带期望码。
    @Query("SELECT r FROM SitePathRule r WHERE r.siteId = :siteId AND r.path = :path")
    Optional<SitePathRule> findBySiteIdAndPath(@Param("siteId") Long siteId,
                                               @Param("path") String path);

    /// 删除某站点的全部规则。
    /// SiteServiceImpl.delete 在物理删除 site 之前调用，无外键依赖。
    @Modifying
    @Query("DELETE FROM SitePathRule r WHERE r.siteId = :siteId")
    long deleteBySiteId(@Param("siteId") Long siteId);
}
