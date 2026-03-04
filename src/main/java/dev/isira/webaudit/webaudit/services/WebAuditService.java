package dev.isira.webaudit.webaudit.services;

import dev.isira.webaudit.webaudit.models.WebAuditResult;

public interface WebAuditService {
    WebAuditResult audit(String url);
}
