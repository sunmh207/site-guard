package com.siteguard.monitor.jsoncondition;

/// JSON 字段条件操作符。操作符本身决定实际值类型以及是否需要 expectedValue，
/// 前端无需再让用户选择一层“值类型”。
public enum JsonConditionOperator {
    IS_TRUE,
    IS_FALSE,
    NUMBER_EQ,
    NUMBER_NE,
    NUMBER_GT,
    NUMBER_GTE,
    NUMBER_LT,
    NUMBER_LTE,
    STRING_EQ,
    STRING_NE,
    STRING_CONTAINS,
    STRING_NOT_CONTAINS,
    EXISTS,
    NOT_EXISTS,
    IS_NULL,
    IS_NOT_NULL;

    public boolean requiresExpectedValue() {
        return switch (this) {
            case NUMBER_EQ, NUMBER_NE, NUMBER_GT, NUMBER_GTE, NUMBER_LT, NUMBER_LTE,
                 STRING_EQ, STRING_NE, STRING_CONTAINS, STRING_NOT_CONTAINS -> true;
            default -> false;
        };
    }

    public boolean isNumberOperator() {
        return switch (this) {
            case NUMBER_EQ, NUMBER_NE, NUMBER_GT, NUMBER_GTE, NUMBER_LT, NUMBER_LTE -> true;
            default -> false;
        };
    }
}
