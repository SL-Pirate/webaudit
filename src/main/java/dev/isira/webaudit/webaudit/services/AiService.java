package dev.isira.webaudit.webaudit.services;

import dev.isira.webaudit.webaudit.models.WebAuditResult;

import java.util.List;

public interface AiService {
    WebAuditResult.AiInsights generateInsights(WebAuditResult.FactualMetrics factualMetrics, String content);

    List<WebAuditResult.Recommendation> recommendations(
            WebAuditResult.FactualMetrics factualMetrics,
            WebAuditResult.AiInsights aiInsights,
            String content
    );
}
