package com.siteguard.monitor.probe;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// [CertForgiveType] 集合的文本化工具：Site.cert_forgive 列（TEXT, nullable）
/// 存储人类可读的 JSON 字符串数组，本工具负责它与 Java Set<CertForgiveType> 的双向转换。
///
/// 例：
///   - null / "" / "[]"                           → 空集（全不放，默认行为）
///   - "[\"chain_incomplete\",\"domain_mismatch\"]" → {CHAIN_INCOMPLETE, DOMAIN_MISMATCH}
///
/// 内部用独立 static ObjectMapper（与 Spring 容器里的 Bean 解耦，避免循环/注入）。
/// 由于项目当前 Jackson 绑定不含 tools.jackson.annotation 包，枚举序列化不走 @JsonValue：
///   - json()：把每个枚举通过 CertForgiveType.getValue() 取短语，再拼成 JSON 字符串数组
///   - parse()：用 Jackson 反序列化 JSON 字符串数组后，逐项 via CertForgiveType.fromValue() 归集
public final class CertForgive {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private CertForgive() {
    }

    /// 把站点级 cert_forgive 文本解析为有序 Set。
    /// null / 空 / 解析失败 → 空集（严格兜底，等价于全不放）。
    public static Set<CertForgiveType> parse(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        List<String> phrases;
        try {
            phrases = MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            // 数据损坏：整体退回空集，站点严格校验
            return Set.of();
        }
        Set<CertForgiveType> out = new LinkedHashSet<>();
        for (var phrase : phrases) {
            var type = CertForgiveType.fromValue(phrase);
            if (type != null) {
                out.add(type);
            }
            // 不认识的值静默跳过，向前兼容未来新增枚举
        }
        return out;
    }

    /// 把枚举集合序列化为 JSON 字符串数组。
    /// null / 空集 → "[]"（非 null，保持非空语义）。
    public static String json(Collection<CertForgiveType> types) {
        if (types == null || types.isEmpty()) {
            return "[]";
        }
        var phrases = new ArrayList<String>(types.size());
        for (var t : types) {
            phrases.add(t.getValue());
        }
        try {
            return MAPPER.writeValueAsString(phrases);
        } catch (Exception e) {
            return "[]";
        }
    }
}
