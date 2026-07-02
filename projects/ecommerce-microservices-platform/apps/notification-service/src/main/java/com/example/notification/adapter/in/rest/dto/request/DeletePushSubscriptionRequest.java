package com.example.notification.adapter.in.rest.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeletePushSubscriptionRequest(
        @NotBlank(message = "endpoint is required") String endpoint
) {}
