package com.example.notification.adapter.in.rest.dto.response;

import com.example.notification.application.result.TemplateResult;

public record TemplateIdResponse(String templateId) {
    public static TemplateIdResponse from(TemplateResult result) {
        return new TemplateIdResponse(result.templateId());
    }
}
