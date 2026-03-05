# AI-Powered Website Audit Tool: Engineering Report

|                 |                                       |
|-----------------|---------------------------------------|
| **Candidate**   | Isira Herath                          |
| **Role**        | AI-Native Software Engineer           |
| **Time Spent**  | 13 hours                              |
| **Live Demo**   | https://webaudit.isira.dev/           |
| **Source Code** | https://github.com/SL-Pirate/webaudit |

---

## 1. Overview

This AI-driven Website Audit Tool is designed to evaluate marketing web pages. Built with a focus on EIGHT25MEDIA's core
priorities — SEO, conversion optimization, content clarity, and UX — the tool cleanly separates deterministic scraping (
factual metrics) from generative analysis (AI insights).

### 🚀 Bonus: Immediate Real-World ROI

While testing the final deliverable, I ran this tool against my own personal portfolio website. The AI analysis
accurately flagged a discrepancy in a dynamic time calculation script that was failing silently, alongside several other
issues, and gave me prioritized recommendations — proving the tool's practical utility before the 24-hour assignment was
even finished!

---

## 2. Architecture Overview

The system is built on a Java-based stack, prioritizing a strict separation of concerns, rapid development, and
pragmatic UX over unnecessary complexity.

- **Core Backend (Spring Boot & Spring Web MVC):** Chosen for speed of development, maintainability, and scalability.
  Being highly comfortable with the Spring ecosystem allowed me to focus purely on business logic and AI orchestration
  within the 24-hour window.

- **Presentation Layer (Thymeleaf + HTMX):** Given the focused scope of the tool, building a separate REST API with a
  heavy frontend framework (like React or Vue) would have been over-engineering. Server-side rendering with Thymeleaf
  handles the UI, while HTMX enables progressive rendering — Step 1: Factual Metrics → Step 2: AI Insights → Step 3: AI
  Recommendations — without page reloads or complex state management.

- **AI Orchestration Layer (Spring AI with Google GenAI):** Spring AI provides a clean abstraction layer that isn't
  tightly coupled to a single vendor, allowing easy model hot-swapping (e.g. Google, OpenAI, Anthropic) via a single
  environment variable. The system currently uses **Gemini 2.5 Flash** for speed and cost-effectiveness.

- **Web Scraping Layer (Playwright Java):** Used to extract raw HTML, parse DOM elements, and calculate deterministic
  metrics. Supports both static and server-side rendered sites, making the tool theoretically usable on any website that
  does not actively block crawlers.

> **Trade-off & Reality Check:** While I have prior experience with Selenium, I chose Playwright to explore its modern
> capabilities. Candidly, I spent a few hours wrestling with Playwright's native dependencies on Arch Linux before
> pragmatically pivoting to a devcontainer. Deployment to Ubuntu was seamless, but in hindsight, sticking with Selenium
> might have saved a few hours of environment debugging.

---

## 3. AI Design Decisions

To ensure the AI produces specific, actionable, and non-generic insights, the following design choices were implemented:

- **Data Grounding:** Instead of asking the AI to "analyze this URL" (which leads to hallucinations or reliance on stale
  training data), the system scrapes the page deterministically first. The AI is only fed the extracted DOM metrics and
  text content, forcing it to reason from reality.

- **Structured Outputs:** Spring AI is used to constrain the model's response to a fixed schema mapping exactly to the
  required categories (SEO, Messaging, CTA, Content Depth, UX) plus an array of 3–5 prioritized recommendations.

- **System Prompting for Persona:** The system prompt explicitly defines the AI's role as a website auditor, UI/UX
  analyst, and Technical SEO expert, tuning the tone to be agency-appropriate and highly critical of vague or fluffy
  content.

---

## 4. Future Improvements

- **Multi-Page Journey Crawling:** Expand from a single-page audit to evaluating the user flow across a landing page and
  its downstream conversion funnel.

- **Competitor Benchmarking:** Accept a competitor URL to generate comparative insights (e.g., *"Competitor X uses 30%
  fewer words and more direct CTAs than this page"*).

---

## 5. Prompt Logs & Reasoning Traces

Below is a snapshot of the orchestration layer, showing how inputs were constructed and how the model was constrained.

### System Prompt

```
You are a website auditor. You will be provided with HTML content (sanitized and stripped of
useless tags such as <script> and <style>). You will analyze it for SEO, accessibility, and
overall user experience, and provide a detailed report on the strengths and weaknesses of the
website along with actionable recommendations for improvement. Your analysis should cover meta
tags, heading structure, image alt text, link structure, page load speed, mobile responsiveness,
and any other relevant factors that contribute to a well-optimized, user-friendly website.
```

### Generate Insights Prompt

```shell
"Based on the following factual metrics and content, provide insights (specific and non-generic) " +
        "on SEO structure, messaging clarity, CTA usage, content depth, and UX concerns.\n" +
        "factualMetrics: " + factualMetrics + "\n" +
        "content: " + content + "\n"
```

- **`content`** — The scraped page content with `<script>` and `<style>` tags stripped. Removing these is critical
  because they typically dominate the raw HTML and would exhaust the model's context window before it reaches any
  meaningful content.

- **`factualMetrics`** — Evaluates to a structured string similar to:
  ```
  FactualMetrics[wordCount=845, headingCounts={H1=1, H2=4, H3=6}, ctaCount=2,
  internalLinksCount=12, externalLinksCount=3, totalImages=5,
  percentImagesMissingAltText=40.0, metaTitle=Home | EIGHT25MEDIA,
  metaDescription=We build high-performing marketing websites focused on SEO and conversion optimization.]
  ```
  This flat representation is intentional — it is easier for the LLM to parse than having metadata scattered throughout
  a larger prompt.

### Generate Recommendations Prompt

```shell
"Based on the following factual metrics, AI insights, and content, provide a list of 3 to 5 " +
        "prioritized concise and actionable recommendations for improving the webpage.\n" +
        "factualMetrics: " + factualMetrics +"\n" +
        "aiInsights: " + aiInsights + "\n" +
        "content: " + content + "\n"
```

- **`factualMetrics`** — Same as above.
- **`content`** — Same as above.
- **`aiInsights`** — Evaluates to a structured string similar to:
  ```
  AiInsight[seoStructure=some seo insight, messagingClarity=some message clarity insight,
  ctaUsage=Some CTA usage insight, contentDepth=content depth insight, uxConcerns=Ux concerns insight]
  ```
