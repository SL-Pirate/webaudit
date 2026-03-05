package dev.isira.webaudit.webaudit.services.impl;

import dev.isira.webaudit.webaudit.models.WebAuditResult;
import dev.isira.webaudit.webaudit.services.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {
    private final ChatClient chatClient;

    @Override
    public WebAuditResult.AiInsights generateInsights(WebAuditResult.FactualMetrics factualMetrics, String content) {
        final var prompt = "Based on the following factual metrics and content, provide insights (specific and non-generic) on SEO structure, messaging clarity, CTA usage, content depth, and UX concerns.\n" +
                "factualMetrics: " + factualMetrics + "\n" +
                "content: " + content + "\n";
        return chatClient
                .prompt(prompt)
                .call()
                .entity(new ParameterizedTypeReference<>() {
                });
    }

    @Override
    public List<WebAuditResult.Recommendation> recommendations(
            WebAuditResult.FactualMetrics factualMetrics,
            WebAuditResult.AiInsights aiInsights,
            String content
    ) {
        final var prompt = "Based on the following factual metrics, AI insights, and content, provide a list of 3 to 5 prioritized concise and actionable recommendations for improving the webpage.\n" +
                "factualMetrics: " + factualMetrics + "\n" +
                "aiInsights: " + aiInsights + "\n" +
                "content: " + content + "\n";
        final List<WebAuditResult.Recommendation> recommendations = chatClient
                .prompt(prompt)
                .call()
                .entity(new ParameterizedTypeReference<>() {
                });
        if (recommendations == null || recommendations.isEmpty()) {
            return List.of();
        }

        return recommendations.subList(0, Math.min(recommendations.size(), 5));
    }
}
