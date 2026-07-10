package com.siteguard.monitor.alert;

/// 告警状态。
///
/// - NORMAL    : 正常，无告警；通知流水中的 NORMAL 表示一次"恢复"事件
/// - ABNORMAL  : 非正常，需要关注；通知流水中的 ABNORMAL 表示一次"新出现异常"事件
///
/// 设计取舍：只区分正常/非正常两态，不区分严重程度。
/// 若未来需要按级别路由（critical 发短信、warning 只发 IM），在此处追加 priority 字段而非扩展此枚举。
public enum AlertStatus {
    NORMAL,
    ABNORMAL
}
