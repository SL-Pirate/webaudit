package dev.isira.webaudit.webaudit.services.impl;

import dev.isira.webaudit.webaudit.models.WebAuditResult;
import dev.isira.webaudit.webaudit.services.AiService;

import java.util.List;

public class GoogleGenAiService implements AiService {
    @Override
    public WebAuditResult.AiInsights generateInsights(WebAuditResult.FactualMetrics factualMetrics, String content) {
        return null;
    }

    @Override
    public List<WebAuditResult.Recommendation> recommendations(WebAuditResult.FactualMetrics factualMetrics, WebAuditResult.AiInsights aiInsights, String content) {
        return List.of();
    }
}
