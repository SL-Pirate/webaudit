package dev.isira.webaudit.webaudit.models;

import java.util.List;
import java.util.Map;

public record WebAuditResult(
        FactualMetrics factualMetrics,
        AiInsights aiInsights,
        List<Recommendation> recommendations
) {
    public record FactualMetrics(
            int wordCount,
            Map<String, Integer> headingCounts,
            int ctaCount,
            int internalLinksCount,
            int externalLinksCount,
            int totalImages,
            double percentImagesMissingAltText,
            String metaTitle,
            String metaDescription
    ) {
    }

    public record AiInsights(
            String seoStructure,
            String messagingClarity,
            String ctaUsage,
            String contentDepth,
            String uxConcerns
    ) {
    }

    public record Recommendation(
            int priority,
            String actionItem,
            String reasoning // Tied to FactualMetrics
    ) {
    }
}
