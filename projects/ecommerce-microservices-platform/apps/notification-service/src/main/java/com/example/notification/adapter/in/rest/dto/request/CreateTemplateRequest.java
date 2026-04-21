package com.example.notification.adapter.in.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTemplateRequest(
        @NotNull(message = "type is required") String type,
        @NotNull(message = "channel is required") String channel,
        @NotBlank(message = "subject is required") String subject,
        @NotBlank(message = "body is required") String body
) {}
