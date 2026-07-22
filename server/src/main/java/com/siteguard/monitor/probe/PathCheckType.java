package com.siteguard.monitor.probe;

/// 子路由健康检查的判定类型。
///
/// 使用 @Enumerated(EnumType.STRING) 持久化，DB 存枚举 name()（大写 HTTP_STATUS / KEYWORD），
/// 与项目现有 @Enumerated(STRING) 惯例一致（SiteCheckHistory / Notification / Site 均采用此方式）。
/// 前端 TS DTO 也约定传大写枚举名，MapStruct 按枚举名自动映射 DTO↔Entity。
public enum PathCheckType {

    /// 按响应 HTTP 状态码判定（2xx/3xx 为健康，其余为异常）。
    /// 默认值，兼容存量子路由规则——存量数据 check_type='HTTP_STATUS' 时走原有状态码分支，行为完全不变。
    HTTP_STATUS,

    /// 按响应体是否包含指定关键字判定（关键字由规则配置，包含为健康）。
    /// 用于状态码正常但业务错误页返回固定文案的场景（如网关 200 + 错误 JSON）。
    KEYWORD;
}
