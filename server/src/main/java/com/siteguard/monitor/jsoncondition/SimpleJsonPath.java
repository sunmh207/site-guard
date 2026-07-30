package com.siteguard.monitor.jsoncondition;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// 受限 JSON 字段路径解析器。
///
/// 只支持对象字段和固定数组下标，刻意不采用完整 JSONPath 库，避免把通配符、过滤器、
/// 递归搜索或脚本能力暴露给健康检查配置。允许输入可选的 `$.` 前缀，保存时统一去掉。
public final class SimpleJsonPath {

    private static final Pattern TOKEN = Pattern.compile("([A-Za-z0-9_-]+)|\\[(\\d+)]");

    private SimpleJsonPath() {
    }

    public static String normalize(String rawPath) {
        if (rawPath == null) {
            throw new IllegalArgumentException("字段路径不能为空");
        }
        String path = rawPath.trim();
        if (path.startsWith("$.")) {
            path = path.substring(2);
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("字段路径不能为空");
        }
        parse(path);
        return path;
    }

    public static ResolvedValue resolve(JsonNode root, String rawPath) {
        String path = normalize(rawPath);
        JsonNode current = root;
        for (PathToken token : parse(path)) {
            if (token.fieldName() != null) {
                if (current == null || !current.isObject() || !current.has(token.fieldName())) {
                    return ResolvedValue.missing();
                }
                current = current.get(token.fieldName());
            } else {
                if (current == null || !current.isArray() || token.arrayIndex() >= current.size()) {
                    return ResolvedValue.missing();
                }
                current = current.get(token.arrayIndex());
            }
        }
        return new ResolvedValue(true, current);
    }

    private static List<PathToken> parse(String path) {
        List<PathToken> tokens = new ArrayList<>();
        int position = 0;
        while (position < path.length()) {
            if (path.charAt(position) == '.') {
                throw invalid(path);
            }
            Matcher matcher = TOKEN.matcher(path);
            matcher.region(position, path.length());
            if (!matcher.lookingAt()) {
                throw invalid(path);
            }
            if (matcher.group(1) != null) {
                tokens.add(PathToken.field(matcher.group(1)));
            } else {
                try {
                    tokens.add(PathToken.index(Integer.parseInt(matcher.group(2))));
                } catch (NumberFormatException e) {
                    throw invalid(path);
                }
            }
            position = matcher.end();
            if (position < path.length()) {
                char next = path.charAt(position);
                if (next == '.') {
                    position++;
                    if (position >= path.length()) {
                        throw invalid(path);
                    }
                } else if (next != '[') {
                    throw invalid(path);
                }
            }
        }
        if (tokens.isEmpty()) {
            throw invalid(path);
        }
        return tokens;
    }

    private static IllegalArgumentException invalid(String path) {
        return new IllegalArgumentException("不支持的字段路径: " + path);
    }

    private record PathToken(String fieldName, int arrayIndex) {
        private static PathToken field(String name) {
            return new PathToken(name, -1);
        }

        private static PathToken index(int index) {
            return new PathToken(null, index);
        }
    }

    public record ResolvedValue(boolean exists, JsonNode value) {
        private static ResolvedValue missing() {
            return new ResolvedValue(false, null);
        }
    }
}
