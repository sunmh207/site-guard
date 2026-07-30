package com.siteguard.monitor.jsoncondition;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/// JSON 条件评估器。正式定时探测和管理端“测试条件”必须复用本组件，
/// 保证测试结果与后台探测采用同一套路径、类型和比较语义。
@Component
@RequiredArgsConstructor
public class JsonConditionEvaluator {

    private static final int MAX_DISPLAY_VALUE_LENGTH = 256;
    private static final int MAX_DETAIL_LENGTH = 2_048;
    private static final int MAX_PERSISTED_DIAGNOSTICS = 3;

    private final ObjectMapper objectMapper;

    public JsonConditionEvaluation evaluate(String responseBody, JsonAssertionConfigDTO config) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (RuntimeException e) {
            String reason = truncate("JSON 解析失败：" + e.getMessage(), MAX_DISPLAY_VALUE_LENGTH);
            return new JsonConditionEvaluation(false, false, List.of(), reason);
        }

        List<JsonConditionDiagnostic> diagnostics = new ArrayList<>();
        for (int i = 0; i < config.conditions().size(); i++) {
            diagnostics.add(evaluateOne(i, root, config.conditions().get(i)));
        }
        boolean matched = config.combinator() == JsonConditionCombinator.ALL
                ? diagnostics.stream().allMatch(JsonConditionDiagnostic::matched)
                : diagnostics.stream().anyMatch(JsonConditionDiagnostic::matched);
        return new JsonConditionEvaluation(true, matched, List.copyOf(diagnostics), buildDetail(diagnostics, matched));
    }

    private JsonConditionDiagnostic evaluateOne(int index, JsonNode root, JsonConditionDTO condition) {
        String path = SimpleJsonPath.normalize(condition.path());
        var resolved = SimpleJsonPath.resolve(root, path);
        JsonActualType actualType = actualType(resolved);
        String actualValue = displayValue(resolved);
        boolean matched;
        String reason;

        if (condition.operator() == JsonConditionOperator.EXISTS) {
            matched = resolved.exists();
            reason = matched ? "字段存在" : "字段不存在";
        } else if (condition.operator() == JsonConditionOperator.NOT_EXISTS) {
            matched = !resolved.exists();
            reason = matched ? "字段不存在" : "字段存在";
        } else if (!resolved.exists()) {
            matched = false;
            reason = "字段不存在";
        } else {
            var result = compare(resolved.value(), condition);
            matched = result.matched();
            reason = result.reason();
        }

        return new JsonConditionDiagnostic(index, path, condition.operator(), matched, actualType,
                actualValue, condition.expectedValue(), truncate(reason, MAX_DISPLAY_VALUE_LENGTH));
    }

    private ComparisonResult compare(JsonNode actual, JsonConditionDTO condition) {
        JsonConditionOperator operator = condition.operator();
        if (operator == JsonConditionOperator.IS_NULL) {
            return result(actual.isNull(), actual.isNull() ? "值为 null" : "值不为 null");
        }
        if (operator == JsonConditionOperator.IS_NOT_NULL) {
            return result(!actual.isNull(), actual.isNull() ? "值为 null" : "值不为 null");
        }
        if (operator == JsonConditionOperator.IS_TRUE || operator == JsonConditionOperator.IS_FALSE) {
            if (!actual.isBoolean()) {
                return result(false, typeMismatch(actualType(actual), "BOOLEAN"));
            }
            boolean expected = operator == JsonConditionOperator.IS_TRUE;
            boolean matched = actual.booleanValue() == expected;
            return result(matched, "实际值为 " + actual.booleanValue() + "，要求为 " + expected);
        }
        if (operator.isNumberOperator()) {
            if (!actual.isNumber()) {
                return result(false, typeMismatch(actualType(actual), "NUMBER"));
            }
            BigDecimal expected = new BigDecimal(condition.expectedValue());
            BigDecimal got = actual.decimalValue();
            int compared = got.compareTo(expected);
            boolean matched = switch (operator) {
                case NUMBER_EQ -> compared == 0;
                case NUMBER_NE -> compared != 0;
                case NUMBER_GT -> compared > 0;
                case NUMBER_GTE -> compared >= 0;
                case NUMBER_LT -> compared < 0;
                case NUMBER_LTE -> compared <= 0;
                default -> throw new IllegalStateException("Unexpected number operator " + operator);
            };
            return result(matched, numberReason(got, operator, expected));
        }
        if (!actual.isString()) {
            return result(false, typeMismatch(actualType(actual), "STRING"));
        }
        String got = actual.stringValue();
        String expected = condition.expectedValue();
        boolean matched = switch (operator) {
            case STRING_EQ -> got.equals(expected);
            case STRING_NE -> !got.equals(expected);
            case STRING_CONTAINS -> got.contains(expected);
            case STRING_NOT_CONTAINS -> !got.contains(expected);
            default -> throw new IllegalStateException("Unexpected string operator " + operator);
        };
        return result(matched, "实际文本「" + truncate(got, 100) + "」，条件 " + operator + "「"
                + truncate(expected, 100) + "」");
    }

    /// 配置校验与路径规范化；返回可直接持久化的干净配置。
    public JsonAssertionConfigDTO validateAndNormalize(JsonAssertionConfigDTO config) {
        if (config == null) {
            throw new IllegalArgumentException("JSON 条件配置不能为空");
        }
        if (config.version() != 1) {
            throw new IllegalArgumentException("暂不支持 JSON 条件配置版本: " + config.version());
        }
        if (config.combinator() == null) {
            throw new IllegalArgumentException("JSON 条件组合方式不能为空");
        }
        if (config.conditions() == null || config.conditions().isEmpty()) {
            throw new IllegalArgumentException("JSON 条件至少需要一条");
        }
        if (config.conditions().size() > 10) {
            throw new IllegalArgumentException("JSON 条件最多允许 10 条");
        }
        List<JsonConditionDTO> normalized = new ArrayList<>();
        for (JsonConditionDTO condition : config.conditions()) {
            if (condition == null || condition.operator() == null) {
                throw new IllegalArgumentException("JSON 条件及操作符不能为空");
            }
            String path = SimpleJsonPath.normalize(condition.path());
            String expected = condition.expectedValue();
            if (condition.operator().requiresExpectedValue()) {
                if (expected == null) {
                    throw new IllegalArgumentException("操作符 " + condition.operator() + " 需要期望值");
                }
                if (condition.operator().isNumberOperator()) {
                    try {
                        new BigDecimal(expected);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("数字期望值不合法: " + expected);
                    }
                }
            } else if (expected != null) {
                throw new IllegalArgumentException("操作符 " + condition.operator() + " 不接受期望值");
            }
            normalized.add(new JsonConditionDTO(path, condition.operator(), expected));
        }
        return new JsonAssertionConfigDTO(1, config.combinator(), List.copyOf(normalized));
    }

    private String buildDetail(List<JsonConditionDiagnostic> diagnostics, boolean matched) {
        List<JsonConditionDiagnostic> selected = matched
                ? diagnostics.stream().filter(JsonConditionDiagnostic::matched).limit(MAX_PERSISTED_DIAGNOSTICS).toList()
                : diagnostics.stream().filter(d -> !d.matched()).limit(MAX_PERSISTED_DIAGNOSTICS).toList();
        String prefix = matched ? "条件满足" : "条件未满足";
        String joined = selected.stream()
                .map(d -> d.path() + "：" + d.reason())
                .reduce((a, b) -> a + "；" + b)
                .orElse(prefix);
        long relevantCount = diagnostics.stream().filter(d -> d.matched() == matched).count();
        if (relevantCount > MAX_PERSISTED_DIAGNOSTICS) {
            joined += "；其余 " + (relevantCount - MAX_PERSISTED_DIAGNOSTICS) + " 条已省略";
        }
        return truncate(joined, MAX_DETAIL_LENGTH);
    }

    private static JsonActualType actualType(SimpleJsonPath.ResolvedValue resolved) {
        return resolved.exists() ? actualType(resolved.value()) : JsonActualType.MISSING;
    }

    private static JsonActualType actualType(JsonNode node) {
        if (node == null) return JsonActualType.MISSING;
        if (node.isNull()) return JsonActualType.NULL;
        if (node.isBoolean()) return JsonActualType.BOOLEAN;
        if (node.isNumber()) return JsonActualType.NUMBER;
        if (node.isString()) return JsonActualType.STRING;
        if (node.isArray()) return JsonActualType.ARRAY;
        return JsonActualType.OBJECT;
    }

    private static String displayValue(SimpleJsonPath.ResolvedValue resolved) {
        if (!resolved.exists()) return null;
        JsonNode node = resolved.value();
        if (node == null || node.isNull()) return "null";
        String value = node.isString() ? node.stringValue() : node.toString();
        return truncate(value, MAX_DISPLAY_VALUE_LENGTH);
    }

    private static String typeMismatch(JsonActualType actual, String expected) {
        return "实际类型为 " + actual + "，要求 " + expected;
    }

    private static String numberReason(BigDecimal actual, JsonConditionOperator operator, BigDecimal expected) {
        return "实际值 " + actual.toPlainString() + "，条件 " + operator + " " + expected.toPlainString();
    }

    private static ComparisonResult result(boolean matched, String reason) {
        return new ComparisonResult(matched, reason);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 1) + "…";
    }

    private record ComparisonResult(boolean matched, String reason) {
    }
}
