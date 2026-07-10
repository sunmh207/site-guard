package com.siteguard.monitor.service;

import com.siteguard.monitor.dto.SitePathRuleDTO;
import com.siteguard.monitor.dto.SitePathRuleListRequest;

import java.util.List;

/// 站点子路由规则服务。
///
/// 三段式 API：
/// - listBySite：拉某站点全部规则，输出含探测状态
/// - set：整批覆盖语义（"全删全插"），请求体由前端决定保留哪些行
/// - delete：按 id 单条删除，幂等（不存在不抛）
public interface SitePathRuleService {

    /// 列出某站点的所有规则
    List<SitePathRuleDTO> listBySite(Long siteId);

    /// 整批覆盖：删除该 site 全部旧规则，保存请求列表
    void set(SitePathRuleListRequest request);

    /// 按 id 删除单条规则
    void delete(Long ruleId);
}
