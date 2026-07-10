package com.siteguard.monitor.dashboard;

import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.site.entity.Site;

import java.util.List;

/// 仪表盘告警源：把一种"告警"翻译成统一 AlertDTO 列表。
///
/// 聚合器注入 `List<DashboardAlertSource>` 并对每个 source 单独 try-catch，
/// 单源失败不影响其他源或整体面板。
///
/// 实现类用 `@Component` 标注，Spring 自动发现；新增告警源 = 新增 class，
/// 不修改聚合器 / DTO / AlertKind。
public interface DashboardAlertSource {

    /// 该 source 输出的告警 kind，必须与产生数据匹配（一个 source 输出一种 kind）
    AlertKind kind();

    /// 基于传入的全部站点产生告警；sites 已从 siteRepo.findAll() 取好，避免 N+1。
    ///
    /// 允许返回空 list（无任何告警）。异常向上抛，由聚合器隔离。
    List<AlertDTO> load(List<Site> allSites);
}
