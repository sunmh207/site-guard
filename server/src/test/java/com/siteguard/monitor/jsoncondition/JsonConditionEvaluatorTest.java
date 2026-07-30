package com.siteguard.monitor.jsoncondition;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonConditionEvaluatorTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final JsonConditionEvaluator evaluator = new JsonConditionEvaluator(objectMapper);

    @Test
    void systemInfo_allConditionsMatched() {
        var config = config(JsonConditionCombinator.ALL,
                condition("checkCrontab", JsonConditionOperator.IS_TRUE, null),
                condition("diskAvailableSpaceRate", JsonConditionOperator.NUMBER_GT, "10"));

        var result = evaluator.evaluate("""
                {"checkCrontab":true,"diskAvailableSpaceRate":18.6}
                """, config);

        assertThat(result.parseSucceeded()).isTrue();
        assertThat(result.matched()).isTrue();
        assertThat(result.conditions()).extracting(JsonConditionDiagnostic::actualValue)
                .containsExactly("true", "18.6");
    }

    @Test
    void numberGreaterThan_equalBoundaryDoesNotMatch() {
        var result = evaluator.evaluate("{\"rate\":10}", config(JsonConditionCombinator.ALL,
                condition("rate", JsonConditionOperator.NUMBER_GT, "10")));

        assertThat(result.matched()).isFalse();
    }

    @Test
    void numericEquality_ignoresDecimalScale() {
        var result = evaluator.evaluate("{\"value\":10.0}", config(JsonConditionCombinator.ALL,
                condition("value", JsonConditionOperator.NUMBER_EQ, "10")));

        assertThat(result.matched()).isTrue();
    }

    @Test
    void strictType_stringNumberDoesNotMatchNumberOperator() {
        var result = evaluator.evaluate("{\"value\":\"18.6\"}", config(JsonConditionCombinator.ALL,
                condition("value", JsonConditionOperator.NUMBER_GT, "10")));

        assertThat(result.matched()).isFalse();
        assertThat(result.conditions().getFirst().reason()).contains("实际类型为 STRING");
    }

    @Test
    void nestedObjectAndFixedArrayIndex_areSupported() {
        var result = evaluator.evaluate("""
                {"data":{"disks":[{"rate":18.6}]}}
                """, config(JsonConditionCombinator.ALL,
                condition("$.data.disks[0].rate", JsonConditionOperator.NUMBER_GTE, "18.6")));

        assertThat(result.matched()).isTrue();
        assertThat(result.conditions().getFirst().path()).isEqualTo("data.disks[0].rate");
    }

    @Test
    void missingAndNull_areDifferent() {
        var result = evaluator.evaluate("{\"value\":null}", config(JsonConditionCombinator.ALL,
                condition("value", JsonConditionOperator.EXISTS, null),
                condition("value", JsonConditionOperator.IS_NULL, null),
                condition("missing", JsonConditionOperator.NOT_EXISTS, null),
                condition("missing", JsonConditionOperator.IS_NULL, null)));

        assertThat(result.conditions()).extracting(JsonConditionDiagnostic::matched)
                .containsExactly(true, true, true, false);
        assertThat(result.conditions()).extracting(JsonConditionDiagnostic::actualType)
                .containsExactly(JsonActualType.NULL, JsonActualType.NULL,
                        JsonActualType.MISSING, JsonActualType.MISSING);
    }

    @Test
    void any_matchesWhenOneConditionMatches() {
        var result = evaluator.evaluate("{\"a\":false,\"b\":true}", config(JsonConditionCombinator.ANY,
                condition("a", JsonConditionOperator.IS_TRUE, null),
                condition("b", JsonConditionOperator.IS_TRUE, null)));

        assertThat(result.matched()).isTrue();
        assertThat(result.conditions()).extracting(JsonConditionDiagnostic::matched)
                .containsExactly(false, true);
    }

    @Test
    void stringOperators_useStrictStringValues() {
        var result = evaluator.evaluate("{\"status\":\"production-ok\"}", config(JsonConditionCombinator.ALL,
                condition("status", JsonConditionOperator.STRING_CONTAINS, "ok"),
                condition("status", JsonConditionOperator.STRING_NOT_CONTAINS, "error"),
                condition("status", JsonConditionOperator.STRING_NE, "ok")));

        assertThat(result.matched()).isTrue();
    }

    @Test
    void invalidJson_returnsBusinessFailureWithoutBody() {
        var result = evaluator.evaluate("not-json", config(JsonConditionCombinator.ALL,
                condition("value", JsonConditionOperator.EXISTS, null)));

        assertThat(result.parseSucceeded()).isFalse();
        assertThat(result.matched()).isFalse();
        assertThat(result.detail()).startsWith("JSON 解析失败");
        assertThat(result.conditions()).isEmpty();
    }

    @Test
    void validate_rejectsUnsupportedPathsAndOperatorValues() {
        assertThatThrownBy(() -> evaluator.validateAndNormalize(config(JsonConditionCombinator.ALL,
                condition("items[*].rate", JsonConditionOperator.NUMBER_GT, "10"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的字段路径");

        assertThatThrownBy(() -> evaluator.validateAndNormalize(config(JsonConditionCombinator.ALL,
                condition("value", JsonConditionOperator.NUMBER_GT, "ten"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数字期望值不合法");

        assertThatThrownBy(() -> evaluator.validateAndNormalize(config(JsonConditionCombinator.ALL,
                condition("value", JsonConditionOperator.IS_TRUE, "true"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不接受期望值");
    }

    private static JsonAssertionConfigDTO config(JsonConditionCombinator combinator,
                                                  JsonConditionDTO... conditions) {
        return new JsonAssertionConfigDTO(1, combinator, List.of(conditions));
    }

    private static JsonConditionDTO condition(String path, JsonConditionOperator operator, String expected) {
        return new JsonConditionDTO(path, operator, expected);
    }
}
