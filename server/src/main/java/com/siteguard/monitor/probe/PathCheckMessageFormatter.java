package com.siteguard.monitor.probe;

import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.jsoncondition.JsonAssertionConfigCodec;

/// 子路由告警、Dashboard 与恢复消息的共享格式器，避免三个入口各自解释检测模式。
public final class PathCheckMessageFormatter {

    private PathCheckMessageFormatter() {
    }

    public static String failure(SitePathRule rule) {
        PathCheckType type = rule.getCheckType() == null ? PathCheckType.HTTP_STATUS : rule.getCheckType();
        if (type == PathCheckType.KEYWORD) {
            if (rule.getLastTextMatched() == null) {
                return String.format("路径 %s 探测失败（%s），期望包含「%s」",
                        rule.getPath(), errorOr(rule, "结果缺失"), rule.getExpectedText());
            }
            return rule.getLastTextMatched()
                    ? String.format("路径 %s 检测异常", rule.getPath())
                    : String.format("路径 %s 未包含期望文本「%s」", rule.getPath(), rule.getExpectedText());
        }
        if (type == PathCheckType.JSON_ASSERT) {
            if (rule.getLastHttpStatus() == null) {
                return String.format("路径 %s 探测失败（%s），期望 HTTP %d 且 JSON 条件满足",
                        rule.getPath(), errorOr(rule, "结果缺失"), rule.getExpectedHttpStatus());
            }
            if (!rule.getLastHttpStatus().equals(rule.getExpectedHttpStatus())) {
                return String.format("路径 %s 返回 %d，期望 HTTP %d；JSON 条件：%s",
                        rule.getPath(), rule.getLastHttpStatus(), rule.getExpectedHttpStatus(),
                        detailOr(rule, "未评估"));
            }
            return String.format("路径 %s JSON 条件未满足：%s",
                    rule.getPath(), detailOr(rule, "结果缺失"));
        }
        Integer got = rule.getLastHttpStatus();
        if (got == null) {
            return String.format("路径 %s 探测失败（%s），期望 %d",
                    rule.getPath(), errorOr(rule, rule.getLastCheckedAt() == null ? "尚未探测" : "结果缺失"),
                    rule.getExpectedHttpStatus());
        }
        return String.format("路径 %s 返回 %d，期望 %d", rule.getPath(), got, rule.getExpectedHttpStatus());
    }

    public static String recovery(String path, SitePathRule rule, JsonAssertionConfigCodec codec) {
        PathCheckType type = rule.getCheckType() == null ? PathCheckType.HTTP_STATUS : rule.getCheckType();
        if (type == PathCheckType.KEYWORD) {
            return "子路由 `" + path + "` 已恢复（期望包含「" + rule.getExpectedText() + "」）";
        }
        if (type == PathCheckType.JSON_ASSERT) {
            String summary = "JSON 条件";
            try {
                var config = codec.decode(rule.getAssertionConfig());
                if (config != null) {
                    summary = (config.combinator().name().equals("ALL") ? "全部" : "任一")
                            + " " + config.conditions().size() + " 条 JSON 条件";
                }
            } catch (IllegalArgumentException ignored) {
            }
            return "子路由 `" + path + "` 已恢复（期望 HTTP " + rule.getExpectedHttpStatus()
                    + "，" + summary + "满足）";
        }
        return "子路由 `" + path + "` 已恢复（期望 " + rule.getExpectedHttpStatus() + "）";
    }

    private static String errorOr(SitePathRule rule, String fallback) {
        return rule.getLastErrorMessage() == null ? fallback : rule.getLastErrorMessage();
    }

    private static String detailOr(SitePathRule rule, String fallback) {
        return rule.getLastJsonDetail() == null ? fallback : rule.getLastJsonDetail();
    }
}
