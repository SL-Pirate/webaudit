package dev.isira.webaudit.webaudit.services.impl;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import dev.isira.webaudit.webaudit.models.WebAuditResult;
import dev.isira.webaudit.webaudit.services.AiService;
import dev.isira.webaudit.webaudit.services.WebAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PlaywrightWebAuditService implements WebAuditService {
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile(
            "<(script|style)[^>]*>.*?</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final List<String> CTA_KEYWORDS = List.of(
            "buy", "sign up", "subscribe", "register", "get started", "download",
            "try", "learn more", "contact", "request", "book", "order", "start",
            "join", "apply", "claim", "free trial", "demo", "add to cart", "shop now"
    );

    private final Browser browser;
    private final AiService aiService;

    @Override
    public String readContent(String url) {
        try (BrowserContext context = browser.newContext()) {
            final var page = context.newPage();
            page.navigate(url);
            page.waitForLoadState();
            return page.content();
        }
    }

    @Override
    public String stripHtml(String html) {
        final var noScriptsOrStyles = stripScriptsAndStyles(html);
        final var noTags = HTML_TAG_PATTERN.matcher(noScriptsOrStyles).replaceAll(" ");
        return WHITESPACE_PATTERN.matcher(noTags).replaceAll(" ").trim();
    }

    @Override
    public String stripScriptsAndStyles(String html) {
        return SCRIPT_STYLE_PATTERN.matcher(html).replaceAll("");
    }

    @Override
    public WebAuditResult.FactualMetrics getFactualMetrics(String url) {
        final var html = readContent(url);
        final var cleanedHtml = stripScriptsAndStyles(html);
        final var plainText = stripHtml(html);

        final var wordCount = countWords(plainText);
        final var headingCounts = countHeadings(cleanedHtml);
        final var ctaCount = countCtas(cleanedHtml);

        final var baseUri = URI.create(url);
        final var host = baseUri.getHost();
        int internalLinksCount = 0;
        int externalLinksCount = 0;

        final var hrefs = extractAttributeValues(cleanedHtml, "a", "href");
        for (final var href : hrefs) {
            if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript:")) {
                continue;
            }
            if (href.startsWith("/") || href.startsWith("./") || href.startsWith("../") || href.contains(host)) {
                internalLinksCount++;
            } else if (href.startsWith("http://") || href.startsWith("https://")) {
                externalLinksCount++;
            } else {
                internalLinksCount++;
            }
        }

        final var imgTags = extractTags(cleanedHtml, "img");
        final var totalImages = imgTags.size();
        final var missingAlt = imgTags.stream()
                .filter(tag -> !tag.toLowerCase().contains("alt=") || tag.matches("(?i).*alt\\s*=\\s*[\"']\\s*[\"'].*"))
                .count();
        final var percentMissingAlt = totalImages > 0 ? (missingAlt * 100.0) / totalImages : 0.0;

        final var metaTitle = extractMetaContent(cleanedHtml, "title");
        final var metaDescription = extractMetaContent(cleanedHtml, "description");

        return new WebAuditResult.FactualMetrics(
                wordCount,
                headingCounts,
                ctaCount,
                internalLinksCount,
                externalLinksCount,
                totalImages,
                percentMissingAlt,
                metaTitle,
                metaDescription
        );
    }

    @Override
    public WebAuditResult audit(String url) {
        final var html = readContent(url);
        final var plainText = stripHtml(html);

        final var factualMetrics = getFactualMetrics(url);
        final var aiInsights = aiService.generateInsights(factualMetrics, plainText);
        final var recommendations = aiService.recommendations(factualMetrics, aiInsights, plainText);

        return new WebAuditResult(factualMetrics, aiInsights, recommendations);
    }

    // --- Private helper methods ---

    private int countWords(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return 0;
        }
        return plainText.split("\\s+").length;
    }

    private Map<String, Integer> countHeadings(String cleanedHtml) {
        final Map<String, Integer> headingCounts = new LinkedHashMap<>();
        for (var i = 1; i <= 6; i++) {
            final var tag = "h" + i;
            final var pattern = Pattern.compile("<" + tag + "[\\s>]", Pattern.CASE_INSENSITIVE);
            final var count = (int) pattern.matcher(cleanedHtml).results().count();
            headingCounts.put(tag, count);
        }
        return headingCounts;
    }

    private int countCtas(String cleanedHtml) {
        // Count buttons
        final var buttonCount = (int) Pattern.compile("<button[\\s>]", Pattern.CASE_INSENSITIVE)
                .matcher(cleanedHtml).results().count();

        // Count input[type=submit] and input[type=button]
        final var inputCtaCount = (int) Pattern.compile(
                "<input[^>]*type\\s*=\\s*[\"'](submit|button)[\"']", Pattern.CASE_INSENSITIVE
        ).matcher(cleanedHtml).results().count();

        // Count links/buttons with CTA keywords in their text
        var keywordCtaCount = 0;
        final var anchorPattern = Pattern.compile("<a\\s[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        final var matcher = anchorPattern.matcher(cleanedHtml);
        while (matcher.find()) {
            final var linkText = HTML_TAG_PATTERN.matcher(matcher.group(1)).replaceAll("").toLowerCase().trim();
            for (final var keyword : CTA_KEYWORDS) {
                if (linkText.contains(keyword)) {
                    keywordCtaCount++;
                    break;
                }
            }
        }

        return buttonCount + inputCtaCount + keywordCtaCount;
    }

    @SuppressWarnings("SameParameterValue")
    private List<String> extractAttributeValues(String html, String tagName, String attrName) {
        final var pattern = Pattern.compile(
                "<" + tagName + "\\s[^>]*" + attrName + "\\s*=\\s*[\"']([^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE
        );
        return pattern.matcher(html).results()
                .map(m -> m.group(1))
                .toList();
    }

    @SuppressWarnings("SameParameterValue")
    private List<String> extractTags(String html, String tagName) {
        // Matches both self-closing and non-self-closing tags
        final var pattern = Pattern.compile(
                "<" + tagName + "\\s[^>]*/?>",
                Pattern.CASE_INSENSITIVE
        );

        return pattern.matcher(html).results()
                .map(MatchResult::group)
                .toList();
    }

    private String extractMetaContent(String html, String name) {
        if ("title".equalsIgnoreCase(name)) {
            final var titlePattern = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            final var matcher = titlePattern.matcher(html);
            return matcher.find() ? matcher.group(1).trim() : "";
        }
        final var metaPattern = Pattern.compile(
                "<meta\\s[^>]*name\\s*=\\s*[\"']" + Pattern.quote(name) + "[\"'][^>]*content\\s*=\\s*[\"']([^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE
        );
        var matcher = metaPattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // Try reversed attribute order (content before name)
        final var reversedPattern = Pattern.compile(
                "<meta\\s[^>]*content\\s*=\\s*[\"']([^\"']*)[\"'][^>]*name\\s*=\\s*[\"']" + Pattern.quote(name) + "[\"']",
                Pattern.CASE_INSENSITIVE
        );
        matcher = reversedPattern.matcher(html);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
}
