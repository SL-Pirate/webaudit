package dev.isira.webaudit.webaudit.services.impl;

import dev.isira.webaudit.webaudit.models.WebAuditResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link AiServiceImpl}.
 * These hit the real Google GenAI API — keep the number of tests minimal.
 */
@SpringBootTest
class AiServiceImplTest {
    @Autowired
    private AiServiceImpl aiServiceImpl;

    // Shared fixture — a small, realistic set of metrics and HTML content
    private static final String SAMPLE_CONTENT = """
            <html>
            <head>
                <title>Best Coffee Beans Online</title>
                <meta name="description" content="Buy the best coffee beans online.">
            </head>
            <body>
                <h1>Welcome to Coffee World</h1>
                <p>We sell premium coffee beans from around the globe.</p>
                <h2>Our Products</h2>
                <img src="beans.jpg">
                <img src="mug.jpg" alt="Coffee mug">
                <a href="/products">Shop Now</a>
                <a href="https://example.com/partner">Our Partner</a>
            </body>
            </html>
            """;

    private static final WebAuditResult.FactualMetrics SAMPLE_METRICS =
            new WebAuditResult.FactualMetrics(
                    15,                        // wordCount
                    Map.of("h1", 1, "h2", 1), // headingCounts
                    1,                         // ctaCount
                    1,                         // internalLinksCount
                    1,                         // externalLinksCount
                    2,                         // totalImages
                    50.0,                      // percentImagesMissingAltText
                    "Best Coffee Beans Online", // metaTitle
                    "Buy the best coffee beans online." // metaDescription
            );

    @Test
    void generateInsights_returnsPopulatedInsights() {
        WebAuditResult.AiInsights insights =
                aiServiceImpl.generateInsights(SAMPLE_METRICS, SAMPLE_CONTENT);

        assertNotNull(insights);
        assertNotNull(insights.seoStructure(), "seoStructure should not be null");
        assertNotNull(insights.messagingClarity(), "messagingClarity should not be null");
        assertNotNull(insights.ctaUsage(), "ctaUsage should not be null");
        assertNotNull(insights.contentDepth(), "contentDepth should not be null");
        assertNotNull(insights.uxConcerns(), "uxConcerns should not be null");

        // Each field should contain some meaningful text
        assertFalse(insights.seoStructure().isBlank(), "seoStructure should not be blank");
        assertFalse(insights.messagingClarity().isBlank(), "messagingClarity should not be blank");
    }

    @Test
    void recommendations_returnsNonEmptyPrioritizedList() {
        // Build insights from the same sample so the recommendation call is coherent
        WebAuditResult.AiInsights insights =
                aiServiceImpl.generateInsights(SAMPLE_METRICS, SAMPLE_CONTENT);

        List<WebAuditResult.Recommendation> recommendations =
                aiServiceImpl.recommendations(SAMPLE_METRICS, insights, SAMPLE_CONTENT);

        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty(), "Should return at least one recommendation");

        // Verify structure of the first recommendation
        WebAuditResult.Recommendation first = recommendations.getFirst();
        assertTrue(first.priority() > 0, "Priority should be a positive number");
        assertNotNull(first.actionItem(), "actionItem should not be null");
        assertFalse(first.actionItem().isBlank(), "actionItem should not be blank");
        assertNotNull(first.reasoning(), "reasoning should not be null");
        assertFalse(first.reasoning().isBlank(), "reasoning should not be blank");
    }
}

