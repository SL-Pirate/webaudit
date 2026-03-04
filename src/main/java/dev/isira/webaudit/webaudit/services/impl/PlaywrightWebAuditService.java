package dev.isira.webaudit.webaudit.services.impl;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import dev.isira.webaudit.webaudit.models.WebAuditResult;
import dev.isira.webaudit.webaudit.services.WebAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaywrightWebAuditService implements WebAuditService {
    private static String stripScriptsAndStylesInternal(Page page) {
        page.evaluate("() => {" +
                "  document.querySelectorAll('script, style').forEach(el => el.remove());" +
                "}");

        return page.content();
    }

    @Override
    public String readContent(String url) {
        return runOnPage(url, Page::content);
    }

    @Override
    public String stripHtml(String url) {
        return runOnPage(url, (page) -> page.innerText("body"));
    }

    @Override
    public String stripScriptsAndStyles(String url) {
        return runOnPage(url, PlaywrightWebAuditService::stripScriptsAndStylesInternal);
    }

    @Override
    public WebAuditResult.FactualMetrics getFactualMetrics(String url) {
        return runOnPage(url, (page) -> {
            // Remove script/style elements so they don't interfere with metric extraction
            stripScriptsAndStylesInternal(page);

            final var plainText = page.innerText("body");
            final var wordCount = countWords(plainText);
            final var headingCounts = countHeadings(page);
            final var ctaCount = countCtas(page);

            // Link analysis – internal vs external
            final var baseUri = URI.create(url);
            final var host = baseUri.getHost();
            var internalLinksCount = 0;
            var externalLinksCount = 0;

            @SuppressWarnings("unchecked") final List<String> allHrefs = (List<String>) page.locator("a[href]")
                    .evaluateAll("els => els.map(e => e.getAttribute('href') || '')");

            for (final var href : allHrefs) {
                if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript:")) {
                    continue;
                }
                try {
                    final var linkUri = URI.create(href);
                    if (linkUri.isAbsolute()) {
                        if (host.equalsIgnoreCase(linkUri.getHost())) {
                            internalLinksCount++;
                        } else {
                            externalLinksCount++;
                        }
                    } else {
                        // Relative links are internal
                        internalLinksCount++;
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Skipping invalid URL in href: {}", href);
                }
            }

            // Images
            final var totalImages = page.locator("img").count();
            final var imagesMissingAlt = page.locator("img:not([alt]), img[alt='']").count();
            final var percentMissingAlt = totalImages > 0
                    ? (imagesMissingAlt * 100.0) / totalImages
                    : 0.0;

            // Meta tags
            final var metaTitle = page.title();
            final var metaDescLocator = page.locator("meta[name='description']");
            final var metaDescription = metaDescLocator.count() > 0
                    ? metaDescLocator.first().getAttribute("content")
                    : "";

            return new WebAuditResult.FactualMetrics(
                    wordCount,
                    headingCounts,
                    ctaCount,
                    internalLinksCount,
                    externalLinksCount,
                    totalImages,
                    percentMissingAlt,
                    metaTitle,
                    metaDescription != null ? metaDescription : ""
            );
        });
    }

    // --- Private helper methods ---

    private int countWords(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return 0;
        }
        return plainText.split("\\s+").length;
    }

    private Map<String, Integer> countHeadings(Page page) {
        final Map<String, Integer> headingCounts = new LinkedHashMap<>();
        for (var level = 1; level <= 6; level++) {
            final var tag = "h" + level;
            final var count = page.locator(tag).count();
            if (count > 0) {
                headingCounts.put(tag, count);
            }
        }

        return headingCounts;
    }

    private int countCtas(Page page) {
        // Count <button>, <input type="submit">, <input type="button">,
        // and <a> elements with role="button" or common CTA class names
        return page.locator(
                "button, input[type='submit'], input[type='button'], " +
                        "a[role='button'], [class*='btn'], [class*='cta']"
        ).count();
    }

    private <T> T runOnPage(String url, PageConsumer<T> consumer) {
        try (
                final var playwright = Playwright.create();
                final var browser = playwright.chromium().launch()
        ) {
            final var page = browser.newPage();
            page.navigate(url);
            page.waitForLoadState();
            return consumer.accept(page);
        }
    }

    private interface PageConsumer<T> {
        T accept(Page page);
    }
}
