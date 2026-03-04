package dev.isira.webaudit.webaudit.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import dev.isira.webaudit.webaudit.models.WebAuditResult;
import dev.isira.webaudit.webaudit.services.AiService;
import dev.isira.webaudit.webaudit.services.WebAuditService;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
@RequiredArgsConstructor
public class WebAuditController {
    private final static Gson GSON = new Gson();

    private final WebAuditService webAuditService;
    private final AiService aiService;

    @GetMapping
    public String index() {
        return "index";
    }

    @GetMapping("/result")
    public String result(@RequestParam(required = false) String url, Model model) {
        if (url == null || url.isBlank()) {
            model.addAttribute("error", "No URL provided. Please enter a valid URL to audit.");
            return "result";
        }
        model.addAttribute("url", url);
        return "result";
    }

    @HxRequest
    @GetMapping("/audit/factual-metrics")
    public String factualMetrics(@RequestParam String url, Model model) {
        final var factualMetrics = webAuditService.getFactualMetrics(url);
        final var content = webAuditService.stripHtml(url);
        model.addAttribute("metrics", factualMetrics);
        model.addAttribute("url", url);
        model.addAttribute("factualMetricsJson", GSON.toJson(factualMetrics));
        model.addAttribute("contentText", content);
        return "fragments/factual-metrics";
    }

    @HxRequest
    @PostMapping("/audit/insights")
    public String insights(
            @RequestParam String url,
            @RequestParam String factualMetricsJson,
            @RequestParam String contentText,
            Model model
    ) {
        final var factualMetrics = GSON.fromJson(factualMetricsJson, WebAuditResult.FactualMetrics.class);
        final var insights = aiService.generateInsights(factualMetrics, contentText);
        model.addAttribute("insights", insights);
        model.addAttribute("url", url);
        model.addAttribute("factualMetricsJson", factualMetricsJson);
        model.addAttribute("insightsJson", GSON.toJson(insights));
        model.addAttribute("contentText", contentText);
        return "fragments/insights";
    }

    @HxRequest
    @PostMapping("/audit/recommendations")
    public String recommendations(
            @RequestParam String factualMetricsJson,
            @RequestParam String insightsJson,
            @RequestParam String contentText,
            Model model
    ) throws JsonProcessingException {
        final var factualMetrics = GSON.fromJson(factualMetricsJson, WebAuditResult.FactualMetrics.class);
        final var aiInsights = GSON.fromJson(insightsJson, WebAuditResult.AiInsights.class);
        final var recommendations = aiService.recommendations(factualMetrics, aiInsights, contentText);
        model.addAttribute("recommendations", recommendations);
        return "fragments/recommendations";
    }
}
