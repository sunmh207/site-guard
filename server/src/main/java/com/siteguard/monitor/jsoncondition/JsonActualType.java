package com.siteguard.monitor.jsoncondition;

/// 条件实际值类型。MISSING 与 NULL 分开，避免把“字段不存在”误判为“字段值为 null”。
public enum JsonActualType {
    MISSING,
    NULL,
    BOOLEAN,
    NUMBER,
    STRING,
    ARRAY,
    OBJECT,
    INVALID_JSON
}
