package com.ragpro.lovemaster.model;

import java.util.List;

/**
 * 恋爱报告 — Spring AI 结构化输出。
 */
public record LoveReport(
        String summary,
        String personalityAnalysis,
        String communicationTips,
        String relationshipAdvice,
        List<String> actionPlan,
        Integer compatibilityScore,
        String warmMessage
) {}
