package dev.isira.webaudit.webaudit.controllers;

import dev.isira.webaudit.webaudit.services.AiService;
import dev.isira.webaudit.webaudit.services.WebAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class WebAuditController {
    private final WebAuditService webAuditService;
    private final AiService aiService;

    @GetMapping
    public String index() {
        return "index";
    }
}
