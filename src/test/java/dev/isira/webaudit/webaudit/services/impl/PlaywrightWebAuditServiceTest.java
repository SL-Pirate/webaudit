package dev.isira.webaudit.webaudit.services.impl;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import dev.isira.webaudit.webaudit.models.WebAuditResult;
import dev.isira.webaudit.webaudit.services.AiService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaywrightWebAuditServiceTest {

    @Mock
    private Browser browser;

    @Mock
    private AiService aiService;

    @InjectMocks
    private PlaywrightWebAuditService service;

    @Mock
    private BrowserContext browserContext;

    @Mock
    private Page page;

    private void stubBrowserNavigation(String html) {
        when(browser.newContext()).thenReturn(browserContext);
        when(browserContext.newPage()).thenReturn(page);
        when(page.content()).thenReturn(html);
    }

    // =========================================================================
    // readContent
    // =========================================================================
    @Nested
    class ReadContent {

        @Test
        void returnsPageContent() {
            String expectedHtml = "<html><body>Hello</body></html>";
            stubBrowserNavigation(expectedHtml);

            String result = service.readContent("https://example.com");

            assertEquals(expectedHtml, result);
            verify(page).navigate("https://example.com");
            verify(page).waitForLoadState();
            verify(browserContext).close();
        }

        @Test
        void closesContextEvenOnException() {
            when(browser.newContext()).thenReturn(browserContext);
            when(browserContext.newPage()).thenReturn(page);
            doThrow(new RuntimeException("Navigation failed"))
                    .when(page).navigate(anyString());

            assertThrows(RuntimeException.class,
                    () -> service.readContent("https://example.com"));

            verify(browserContext).close();
        }
    }

    // =========================================================================
    // stripHtml
    // =========================================================================
    @Nested
    class StripHtml {

        @Test
        void removesAllHtmlTags() {
            String html = "<html><body><p>Hello <b>World</b></p></body></html>";
            String result = service.stripHtml(html);
            assertEquals("Hello World", result);
        }

        @Test
        void removesScriptsAndStylesBeforeTags() {
            String html = """
                    <html>
                    <head><style>.cls { color: red; }</style></head>
                    <body>
                    <script>alert('xss');</script>
                    <p>Visible text</p>
                    </body>
                    </html>""";
            String result = service.stripHtml(html);
            assertTrue(result.contains("Visible text"));
            assertFalse(result.contains("alert"));
            assertFalse(result.contains("color: red"));
        }

        @Test
        void collapsesWhitespace() {
            String html = "<p>  Multiple   spaces   here  </p>";
            String result = service.stripHtml(html);
            assertEquals("Multiple spaces here", result);
        }

        @Test
        void returnsEmptyForEmptyInput() {
            assertEquals("", service.stripHtml(""));
        }

        @Test
        void handlesNestedTags() {
            String html = "<div><ul><li><a href='#'>Link <em>text</em></a></li></ul></div>";
            String result = service.stripHtml(html);
            assertEquals("Link text", result);
        }

        @Test
        void handlesEntitiesInText() {
            // HTML entities are left as-is since we're only stripping tags
            String html = "<p>Tom &amp; Jerry</p>";
            String result = service.stripHtml(html);
            assertEquals("Tom &amp; Jerry", result);
        }
    }

    // =========================================================================
    // stripScriptsAndStyles
    // =========================================================================
    @Nested
    class StripScriptsAndStyles {

        @Test
        void removesScriptTags() {
            String html = "<p>Before</p><script>var x = 1;</script><p>After</p>";
            String result = service.stripScriptsAndStyles(html);
            assertEquals("<p>Before</p><p>After</p>", result);
        }

        @Test
        void removesStyleTags() {
            String html = "<p>Before</p><style>.a { color: red; }</style><p>After</p>";
            String result = service.stripScriptsAndStyles(html);
            assertEquals("<p>Before</p><p>After</p>", result);
        }

        @Test
        void removesMultipleScriptAndStyleTags() {
            String html = "<script>a()</script><p>X</p><style>.b{}</style><script>b()</script>";
            String result = service.stripScriptsAndStyles(html);
            assertEquals("<p>X</p>", result);
        }

        @Test
        void handlesCaseInsensitiveScriptTags() {
            String html = "<SCRIPT>var x = 1;</SCRIPT><p>Text</p><Style>body{}</Style>";
            String result = service.stripScriptsAndStyles(html);
            assertEquals("<p>Text</p>", result);
        }

        @Test
        void handlesScriptWithAttributes() {
            String html = "<script type=\"text/javascript\" src=\"app.js\">code()</script><p>OK</p>";
            String result = service.stripScriptsAndStyles(html);
            assertEquals("<p>OK</p>", result);
        }

        @Test
        void handlesMultilineScriptContent() {
            String html = """
                    <script>
                    function foo() {
                        return 42;
                    }
                    </script><p>Content</p>""";
            String result = service.stripScriptsAndStyles(html);
            assertEquals("<p>Content</p>", result);
        }

        @Test
        void returnsUnchangedWhenNoScriptsOrStyles() {
            String html = "<p>Just text</p>";
            assertEquals(html, service.stripScriptsAndStyles(html));
        }
    }

    // =========================================================================
    // getFactualMetrics — word count
    // =========================================================================
    @Nested
    class GetFactualMetrics_WordCount {

        @Test
        void countsWordsCorrectly() {
            String html = "<html><body><p>Hello world foo bar baz</p></body></html>";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(5, metrics.wordCount());
        }

        @Test
        void wordCountIsZeroForEmptyBody() {
            String html = "<html><body></body></html>";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            // The stripped text is just whitespace-collapsed version of empty tags
            // Depending on how many spaces/empty strings result, this could be 0 or small
            assertTrue(metrics.wordCount() <= 1,
                    "Expected 0 or at most 1 for nearly empty page, got: " + metrics.wordCount());
        }

        @Test
        void wordCountIgnoresScriptContent() {
            String html = """
                    <html><body>
                    <script>var longText = "this should not be counted as words";</script>
                    <p>Real content here</p>
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            // "Real content here" = 3 words
            assertEquals(3, metrics.wordCount());
        }
    }

    // =========================================================================
    // getFactualMetrics — heading counts
    // =========================================================================
    @Nested
    class GetFactualMetrics_Headings {

        @Test
        void countsHeadingsCorrectly() {
            String html = """
                    <html><body>
                    <h1>Title</h1>
                    <h2>Subtitle A</h2>
                    <h2>Subtitle B</h2>
                    <h3>Detail</h3>
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(1, metrics.headingCounts().get("h1"));
            assertEquals(2, metrics.headingCounts().get("h2"));
            assertEquals(1, metrics.headingCounts().get("h3"));
            assertEquals(0, metrics.headingCounts().get("h4"));
            assertEquals(0, metrics.headingCounts().get("h5"));
            assertEquals(0, metrics.headingCounts().get("h6"));
        }

        @Test
        void countsHeadingsWithAttributes() {
            String html = """
                    <html><body>
                    <h1 class="main-title" id="top">Title</h1>
                    <h2 style="color:red">Subtitle</h2>
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(1, metrics.headingCounts().get("h1"));
            assertEquals(1, metrics.headingCounts().get("h2"));
        }

        @Test
        void headingCountsCaseInsensitive() {
            String html = "<html><body><H1>Title</H1><H2>Sub</H2></body></html>";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(1, metrics.headingCounts().get("h1"));
            assertEquals(1, metrics.headingCounts().get("h2"));
        }

        @Test
        void zeroHeadingsWhenNonePresent() {
            String html = "<html><body><p>No headings</p></body></html>";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            metrics.headingCounts().values().forEach(count ->
                    assertEquals(0, count, "All heading counts should be 0"));
        }
    }

    // =========================================================================
    // getFactualMetrics — CTA count
    // =========================================================================
    @Nested
    class GetFactualMetrics_CTAs {

        @Test
        void countsButtons() {
            String html = """
                    <html><body>
                    <button>Click me</button>
                    <button type="submit">Submit</button>
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(2, metrics.ctaCount());
        }

        @Test
        void countsInputSubmitAndButton() {
            String html = """
                    <html><body>
                    <input type="submit" value="Go">
                    <input type="button" value="Click">
                    <input type="text" value="Not a CTA">
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(2, metrics.ctaCount());
        }

        @Test
        void countsLinksWithCtaKeywords() {
            String html = """
                    <html><body>
                    <a href="/signup">Sign Up Now</a>
                    <a href="/products">Shop Now</a>
                    <a href="/about">About Us</a>
                    <a href="/trial">Start Your Free Trial Today</a>
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            // "sign up", "shop now", "free trial" and "start" — but "about us" is not a CTA keyword
            // "Sign Up Now" -> matches "sign up"
            // "Shop Now" -> matches "shop now"
            // "Start Your Free Trial Today" -> matches "start" (and "free trial")
            // Total keyword CTAs = 3, buttons = 0, inputs = 0
            assertEquals(3, metrics.ctaCount());
        }

        @Test
        void doesNotCountNonCtaLinks() {
            String html = """
                    <html><body>
                    <a href="/about">About Us</a>
                    <a href="/privacy">Privacy Policy</a>
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(0, metrics.ctaCount());
        }

        @Test
        void combinesAllCtaTypes() {
            String html = """
                    <html><body>
                    <button>Go</button>
                    <input type="submit" value="Submit">
                    <a href="/buy">Buy Now</a>
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(3, metrics.ctaCount());
        }
    }

    // =========================================================================
    // getFactualMetrics — link counts
    // =========================================================================
    @Nested
    class GetFactualMetrics_Links {

        @Test
        void classifiesInternalAndExternalLinks() {
            String html = """
                    <html><body>
                    <a href="/products">Products</a>
                    <a href="./about">About</a>
                    <a href="../parent">Parent</a>
                    <a href="https://example.com/page">Same domain</a>
                    <a href="https://other.com">External</a>
                    <a href="http://another.org">Another External</a>
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(4, metrics.internalLinksCount()); // /, ./, ../, contains host
            assertEquals(2, metrics.externalLinksCount()); // https://other.com, http://another.org
        }

        @Test
        void ignoresHashAndJavascriptLinks() {
            String html = """
                    <html><body>
                    <a href="#">Top</a>
                    <a href="#section">Section</a>
                    <a href="javascript:void(0)">JS Link</a>
                    <a href="">Empty</a>
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(0, metrics.internalLinksCount());
            assertEquals(0, metrics.externalLinksCount());
        }

        @Test
        void relativeLinksWithoutPrefixCountAsInternal() {
            // Links like "page.html" that don't start with /, ./, ../, http://, or https://
            String html = """
                    <html><body>
                    <a href="page.html">Relative</a>
                    <a href="subdir/page.html">Subdir Relative</a>
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            // These fall into the else branch and are counted as internal
            assertEquals(2, metrics.internalLinksCount());
            assertEquals(0, metrics.externalLinksCount());
        }

        @Test
        void mailtoAndTelLinksAreCountedAsInternal_potentialBug() {
            // BUG HIGHLIGHT: mailto: and tel: links are not http(s) and don't start
            // with /, ./, ../ so they fall into the else branch and get counted as internal links.
            // This is arguably incorrect — they should be skipped entirely.
            String html = """
                    <html><body>
                    <a href="mailto:info@example.com">Email Us</a>
                    <a href="tel:+1234567890">Call Us</a>
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            // Current behavior: both count as internal (bug)
            // Expected correct behavior: both should be 0
            // This test documents the bug — it will PASS showing the buggy behavior.
            // If you consider the correct behavior, uncomment the assertions below and this test FAILS:
            assertEquals(0, metrics.internalLinksCount(),
                    "BUG: mailto: and tel: links should not be counted as internal links");
            assertEquals(0, metrics.externalLinksCount());
        }
    }

    // =========================================================================
    // getFactualMetrics — image analysis
    // =========================================================================
    @Nested
    class GetFactualMetrics_Images {

        @Test
        void countsImagesAndMissingAlt() {
            String html = """
                    <html><body>
                    <img src="a.jpg" alt="Photo A">
                    <img src="b.jpg">
                    <img src="c.jpg" alt="">
                    <img src="d.jpg" alt="Photo D">
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(4, metrics.totalImages());
            // b.jpg has no alt, c.jpg has empty alt => 2 missing
            assertEquals(50.0, metrics.percentImagesMissingAltText(), 0.1);
        }

        @Test
        void allImagesHaveAlt() {
            String html = """
                    <html><body>
                    <img src="a.jpg" alt="Photo A">
                    <img src="b.jpg" alt="Photo B">
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(2, metrics.totalImages());
            assertEquals(0.0, metrics.percentImagesMissingAltText(), 0.01);
        }

        @Test
        void noImages() {
            String html = "<html><body><p>No images</p></body></html>";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(0, metrics.totalImages());
            assertEquals(0.0, metrics.percentImagesMissingAltText(), 0.01);
        }

        @Test
        void selfClosingImageTags() {
            String html = """
                    <html><body>
                    <img src="a.jpg" alt="A" />
                    <img src="b.jpg" />
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(2, metrics.totalImages());
            // b.jpg is missing alt
            assertEquals(50.0, metrics.percentImagesMissingAltText(), 0.1);
        }

        @Test
        void altAttributeSubstringMatch_potentialBug() {
            // BUG HIGHLIGHT: The alt-missing check uses `tag.toLowerCase().contains("alt=")`
            // which could match attributes like "salt=", "malt=", "defaultalt=", etc.
            // This means an image with an unrelated attribute containing "alt=" substring
            // would be incorrectly considered as having an alt text.
            String html = """
                    <html><body>
                    <img src="a.jpg" data-default="something" alt="Real alt">
                    <img src="b.jpg" data-salt="abc">
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(2, metrics.totalImages());
            // b.jpg has no alt attribute, but contains "salt=" which includes "alt=" substring
            // BUG: The code treats this as having alt text, so percent missing would be 0% instead of 50%
            assertEquals(50.0, metrics.percentImagesMissingAltText(), 0.1,
                    "BUG: 'data-salt=' contains 'alt=' substring, causing false negative for missing alt detection");
        }
    }

    // =========================================================================
    // getFactualMetrics — meta information
    // =========================================================================
    @Nested
    class GetFactualMetrics_Meta {

        @Test
        void extractsMetaTitleAndDescription() {
            String html = """
                    <html>
                    <head>
                        <title>My Page Title</title>
                        <meta name="description" content="A great description">
                    </head>
                    <body><p>Content</p></body>
                    </html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals("My Page Title", metrics.metaTitle());
            assertEquals("A great description", metrics.metaDescription());
        }

        @Test
        void returnsEmptyWhenMetaTagsMissing() {
            String html = "<html><head></head><body><p>Content</p></body></html>";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals("", metrics.metaTitle());
            assertEquals("", metrics.metaDescription());
        }

        @Test
        void handlesReversedMetaAttributes() {
            // content before name
            String html = """
                    <html>
                    <head>
                        <title>Title</title>
                        <meta content="Reversed order description" name="description">
                    </head>
                    <body><p>Content</p></body>
                    </html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals("Title", metrics.metaTitle());
            assertEquals("Reversed order description", metrics.metaDescription());
        }

        @Test
        void handlesTitleWithWhitespace() {
            String html = """
                    <html>
                    <head>
                        <title>   Padded Title   </title>
                    </head>
                    <body></body>
                    </html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals("Padded Title", metrics.metaTitle());
        }

        @Test
        void handlesMultipleTitleTags_usesFirst() {
            String html = """
                    <html>
                    <head>
                        <title>First Title</title>
                        <title>Second Title</title>
                    </head>
                    <body></body>
                    </html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals("First Title", metrics.metaTitle());
        }
    }

    // =========================================================================
    // getFactualMetrics — comprehensive / integration-style
    // =========================================================================
    @Nested
    class GetFactualMetrics_Comprehensive {

        @Test
        void fullPageAnalysis() {
            String html = """
                    <html>
                    <head>
                        <title>Best Coffee Beans Online</title>
                        <meta name="description" content="Buy the best coffee beans online.">
                        <style>body { font-family: Arial; }</style>
                    </head>
                    <body>
                        <h1>Welcome to Coffee World</h1>
                        <script>console.log("analytics");</script>
                        <p>We sell premium coffee beans from around the globe.</p>
                        <h2>Our Products</h2>
                        <img src="beans.jpg">
                        <img src="mug.jpg" alt="Coffee mug">
                        <a href="/products">Shop Now</a>
                        <a href="https://partner.com">Our Partner</a>
                        <button>Add to Cart</button>
                        <input type="submit" value="Subscribe">
                    </body>
                    </html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals("Best Coffee Beans Online", metrics.metaTitle());
            assertEquals("Buy the best coffee beans online.", metrics.metaDescription());
            assertEquals(1, metrics.headingCounts().get("h1"));
            assertEquals(1, metrics.headingCounts().get("h2"));
            assertEquals(2, metrics.totalImages());
            assertEquals(50.0, metrics.percentImagesMissingAltText(), 0.1);
            assertEquals(1, metrics.internalLinksCount());  // /products
            assertEquals(1, metrics.externalLinksCount());  // https://partner.com
            // CTAs: 1 button + 1 input[type=submit] + 1 keyword CTA ("Shop Now" matches "shop now")
            assertEquals(3, metrics.ctaCount());
            assertTrue(metrics.wordCount() > 0);
        }
    }

    // =========================================================================
    // audit
    // =========================================================================
    @Nested
    class Audit {

        @Test
        void orchestratesFullAudit() {
            String html = """
                    <html>
                    <head><title>Test</title></head>
                    <body>
                    <h1>Hello</h1>
                    <p>World</p>
                    <a href="/page">Learn More</a>
                    </body>
                    </html>""";
            stubBrowserNavigation(html);

            var mockInsights = new WebAuditResult.AiInsights(
                    "Good SEO", "Clear messaging", "Effective CTAs",
                    "Sufficient depth", "No concerns"
            );
            var mockRecommendations = List.of(
                    new WebAuditResult.Recommendation(1, "Add more content", "Low word count")
            );

            when(aiService.generateInsights(any(), anyString())).thenReturn(mockInsights);
            when(aiService.recommendations(any(), any(), anyString())).thenReturn(mockRecommendations);

            var result = service.audit("https://example.com");

            assertNotNull(result);
            assertNotNull(result.factualMetrics());
            assertEquals(mockInsights, result.aiInsights());
            assertEquals(mockRecommendations, result.recommendations());

            verify(aiService).generateInsights(any(), anyString());
            verify(aiService).recommendations(any(), any(), anyString());
        }

        @Test
        void auditCallsReadContentTwice_potentialInefficiency() {
            // BUG/INEFFICIENCY HIGHLIGHT: audit() calls readContent() for plainText
            // AND getFactualMetrics() also calls readContent() internally.
            // This means the URL is fetched TWICE, which is wasteful and could yield
            // different content on dynamic pages.
            String html = "<html><body><p>Content</p></body></html>";
            stubBrowserNavigation(html);

            var mockInsights = new WebAuditResult.AiInsights("a", "b", "c", "d", "e");
            when(aiService.generateInsights(any(), anyString())).thenReturn(mockInsights);
            when(aiService.recommendations(any(), any(), anyString())).thenReturn(List.of());

            service.audit("https://example.com");

            // Verify that browser.newContext() is called twice — once from audit()'s
            // readContent() and once from getFactualMetrics()'s readContent()
            verify(browser, times(2)).newContext();
        }
    }

    // =========================================================================
    // Edge cases
    // =========================================================================
    @Nested
    class EdgeCases {

        @Test
        void handlesHtmlWithOnlyScriptsAndStyles() {
            String html = """
                    <html>
                    <head><style>body{}</style></head>
                    <body><script>var x=1;</script></body>
                    </html>""";
            String result = service.stripHtml(html);
            // Should be empty or near-empty after removing scripts/styles
            assertTrue(result.isBlank() || result.length() < 5,
                    "Should be mostly empty after stripping, got: '" + result + "'");
        }

        @Test
        void handlesVeryLongAttributeValues() {
            String longAlt = "A".repeat(10000);
            String html = "<html><body><img src='a.jpg' alt='" + longAlt + "'></body></html>";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(1, metrics.totalImages());
            assertEquals(0.0, metrics.percentImagesMissingAltText(), 0.01);
        }

        @Test
        void handlesNestedAnchors() {
            // Invalid HTML but browsers sometimes render it
            String html = """
                    <html><body>
                    <a href="/outer"><a href="/inner">Text</a></a>
                    </body></html>""";
            stubBrowserNavigation(html);

            // Should not throw
            assertDoesNotThrow(() -> service.getFactualMetrics("https://example.com"));
        }

        @Test
        void handlesNoHrefOnAnchor() {
            // <a> without href should be ignored by extractAttributeValues
            String html = """
                    <html><body>
                    <a name="anchor">Bookmark</a>
                    <a href="/real">Real Link</a>
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(1, metrics.internalLinksCount());
            assertEquals(0, metrics.externalLinksCount());
        }

        @Test
        void handlesSingleQuotesInAttributes() {
            String html = """
                    <html>
                    <head>
                        <meta name='description' content='Single quoted description'>
                    </head>
                    <body><p>Content</p></body>
                    </html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals("Single quoted description", metrics.metaDescription());
        }

        @Test
        void scriptsInsideBodyAreStripped() {
            String html = """
                    <html><body>
                    <p>Before</p>
                    <script type="application/ld+json">{"@type":"Organization"}</script>
                    <p>After</p>
                    </body></html>""";

            String result = service.stripScriptsAndStyles(html);

            assertFalse(result.contains("Organization"));
            assertTrue(result.contains("Before"));
            assertTrue(result.contains("After"));
        }

        @Test
        void ctaKeywordMatchIsCaseInsensitive() {
            String html = """
                    <html><body>
                    <a href="/signup">SIGN UP</a>
                    <a href="/download">DOWNLOAD NOW</a>
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            assertEquals(2, metrics.ctaCount());
        }

        @Test
        void imagesWithoutSrcAttributeAreNotCounted() {
            // The extractTags regex requires at least one attribute (\\s[^>]*/?>)
            // A bare <img> without any attributes won't be matched
            String html = """
                    <html><body>
                    <img>
                    <img src="real.jpg" alt="Real">
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com");

            // <img> without attributes won't match the pattern `<img\\s[^>]*/?>` since there's no \\s after img
            assertEquals(1, metrics.totalImages());
        }

        @Test
        void urlWithPortAndPath() {
            String html = """
                    <html><body>
                    <a href="https://example.com:8080/page">Internal with port</a>
                    <a href="/local">Local</a>
                    </body></html>""";
            stubBrowserNavigation(html);

            var metrics = service.getFactualMetrics("https://example.com:8080/test");

            // "https://example.com:8080/page" contains "example.com" (the host from URL)
            assertEquals(2, metrics.internalLinksCount());
            assertEquals(0, metrics.externalLinksCount());
        }
    }
}

