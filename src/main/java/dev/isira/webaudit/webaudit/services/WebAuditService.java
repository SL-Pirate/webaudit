package dev.isira.webaudit.webaudit.services;

import dev.isira.webaudit.webaudit.models.WebAuditResult;

public interface WebAuditService {
    /**
     * @param url - URL of the webpage to audit
     * @return - Raw HTML content of the webpage. This will be used for both factual metric extraction and AI insights generation.
     */
    String readContent(String url);

    /**
     * @param url - URL of the webpage to audit
     * @return - Cleaned text content with all HTML tags removed. This will be used for word count, messaging clarity analysis, and content depth evaluation.
     */
    String stripHtml(String url);

    /**
     * @param url - URL of the webpage to audit
     * @return - HTML content with all <script> and <style> tags (and their content) removed. This will be used for accurate extraction of headings, links, images, and other structural elements without interference from scripts or styles.
     */
    String stripScriptsAndStyles(String url);

    /**
     * @param url - URL of the webpage to audit
     * @return - A structured object containing all factual metrics extracted from the webpage, such as word count, heading counts, CTA count, link counts, image analysis, and meta information. This will be used as the basis for generating AI insights and recommendations.
     */
    WebAuditResult.FactualMetrics getFactualMetrics(String url);
}
