package com.siteguard.monitor.alert;

import com.siteguard.site.entity.Site;

import java.time.Clock;
import java.util.Set;

/// 单个告警维度的检测器接口。
///
/// 一个实现对应一个 AlertKind（例如可用性、证书、域名）。
/// eval 是无状态纯函数：相同 (site, clock) 始终返回相同 Set<EvalResult>。
/// 边沿判断（集合差）、状态持久化、事件派发由 AlertDetectionService 统一处理。
public interface AlertDefinition {

    /// 检测器对应的告警维度
    AlertKind kind();

    /// 对单个站点执行一次探测，返回该站点本次 tick 的"真值集合"。
    ///
    /// 每条 EvalResult 对应一个潜在 event：
    /// - PATH_CHECK：每条 failing 路径一条 EvalResult(bucket=pathKey, ABNORMAL, msg)；
    ///   全 OK 时返回空集；无规则时也返回空集
    /// - 其他 kind：通常 0 或 1 条
    ///
    /// 返回空集表示该站点在此维度上当前无事件可发（无规则 / 全 OK / 当前不在该 kind 的判定窗口）。
    /// 重要：空集不应被解读为"已恢复"——AlertDetectionService 对非 PATH_CHECK 会用此判定
    /// 区分"暂无可发事件"与"彻底恢复"，避免抖动场景的误报。
    Set<EvalResult> eval(Site site, Clock clock);

    /// 单条真值：bucket 是状态机 key（PATH_CHECK 时即为 pathKey）。
    record EvalResult(String bucket, AlertStatus status, String message) {}
}