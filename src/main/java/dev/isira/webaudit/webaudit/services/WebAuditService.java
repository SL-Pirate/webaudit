package dev.isira.webaudit.webaudit.services;

import dev.isira.webaudit.webaudit.models.WebAuditResult;

public interface WebAuditService {
    /**
     * @param url - URL of the webpage to audit
     * @return - Raw HTML content of the webpage. This will be used for both factual metric extraction and AI insights generation.
     */
    String readContent(String url);

    /**
     * @param html - Raw HTML content of the webpage
     * @return - Cleaned text content with all HTML tags removed. This will be used for word count, messaging clarity analysis, and content depth evaluation.
     */
    String stripHtml(String html);

    /**
     * @param html - Raw HTML content of the webpage
     * @return - HTML content with all <script> and <style> tags (and their content) removed. This will be used for accurate extraction of headings, links, images, and other structural elements without interference from scripts or styles.
     */
    String stripScriptsAndStyles(String html);

    /**
     * @param url - URL of the webpage to audit
     * @return - A structured object containing all factual metrics extracted from the webpage, such as word count, heading counts, CTA count, link counts, image analysis, and meta information. This will be used as the basis for generating AI insights and recommendations.
     */
    WebAuditResult.FactualMetrics getFactualMetrics(String url);

    /**
     * A combined method that orchestrates the entire auditing process for a given webpage URL. It retrieves the webpage content, extracts factual metrics, generates AI insights based on those metrics and the content, and then produces prioritized recommendations for improving the webpage. The result is a comprehensive audit report that includes all relevant information for understanding the current state of the webpage and actionable steps for enhancement.
     * Reuses the other methods in this interface to perform the necessary steps of the auditing process, ensuring a clean separation of concerns and modularity in the implementation.
     *
     * @param url - URL of the webpage to audit
     * @return - A comprehensive audit result that includes factual metrics, AI-generated insights, and prioritized recommendations for improving the webpage. This method orchestrates the entire auditing process, from content retrieval and metric extraction to AI analysis and recommendation generation.
     */
    WebAuditResult audit(String url);
}
