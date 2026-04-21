package com.example.notification.adapter.in.rest.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateTemplateRequest(
        @NotBlank(message = "subject is required") String subject,
        @NotBlank(message = "body is required") String body
) {}
