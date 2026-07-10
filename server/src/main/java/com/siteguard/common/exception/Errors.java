package com.siteguard.common.exception;

public class Errors {
    public static final ErrorCode BAD_REQUEST = ErrorCode.of(400, "BAD_REQUEST", "请求格式不正确");

    public static final ErrorCode INVALID_ARGUMENT = ErrorCode.of(400, "INVALID_ARGUMENT", "参数不正确");

    public static final ErrorCode UNAUTHORIZED = ErrorCode.of(401, "UNAUTHORIZED", "未登陆");

    public static final ErrorCode ACCESS_DENIED = ErrorCode.of(403, "ACCESS_DENIED", "无权访问");

    public static final ErrorCode NOT_FOUND = ErrorCode.of(404, "NOT_FOUND", "对象不存在");

    public static final ErrorCode CONFLICT = ErrorCode.of(409, "CONFLICT", "资源冲突");

    public static final ErrorCode INTERNAL_ERROR = ErrorCode.of(500, "INTERNAL_ERROR", "服务器内部错误");

    public static final ErrorCode CATEGORY_HAS_CHILDREN = ErrorCode.of(409, "CATEGORY_HAS_CHILDREN", "分类下还有子分类");

    public static final ErrorCode INVALID_PARENT = ErrorCode.of(422, "INVALID_PARENT", "父级分类不合法");
}
