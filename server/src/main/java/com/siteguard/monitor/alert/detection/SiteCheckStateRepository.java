package com.siteguard.monitor.alert.detection;

import com.siteguard.monitor.alert.AlertKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SiteCheckStateRepository extends JpaRepository<SiteCheckState, SiteCheckStateId> {

    /// 拉取某 AlertKind 下全部 (siteId, bucket) 快照，按 siteId 升序。
    /// 用于 AlertDetectionService 单次扫描全量 (site, kind) 对比。
    ///
    /// 使用 SpEL 提取 enum name 进行字符串匹配：embeddable 字段为 String，
    /// 不支持直接传 enum 参数；表达式 :#{#kind.name()} 在查询绑定时转换为字符串。
    @Query("SELECT s FROM SiteCheckState s WHERE s.id.alertKind = :#{#kind.name()} ORDER BY s.id.siteId ASC")
    List<SiteCheckState> findByAlertKind(@Param("kind") AlertKind kind);

    /// 批量删除某 (siteId, alertKind) 下 bucket 落在传入集合的行。
    ///
    /// 用于 AlertDetectionService 集合差算法：
    /// 当旧集合 → 新集合有"消失的 bucket"（恢复 / 状态机退出）时一次性删除对应行。
    /// 集合差由调用方算好后传进来；这里只负责 SQL 执行，避免 N+1。
    ///
    /// 返回 int 是 Spring Data 惯例（受影响行数），便于测试断言。
    @Modifying
    @Query("DELETE FROM SiteCheckState s WHERE s.id.siteId = :siteId " +
           "AND s.id.alertKind = :#{#alertKind.name()} " +
           "AND s.id.bucket IN :buckets")
    int deleteBySiteIdAndAlertKindAndBucketIn(
            @Param("siteId") Long siteId,
            @Param("alertKind") AlertKind alertKind,
            @Param("buckets") Collection<String> buckets);
}
