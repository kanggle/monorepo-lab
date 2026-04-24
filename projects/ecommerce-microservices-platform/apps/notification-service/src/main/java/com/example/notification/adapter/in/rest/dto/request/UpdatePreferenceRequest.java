package com.example.notification.adapter.in.rest.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdatePreferenceRequest(
        @NotNull(message = "emailEnabled is required") Boolean emailEnabled,
        @NotNull(message = "smsEnabled is required") Boolean smsEnabled,
        @NotNull(message = "pushEnabled is required") Boolean pushEnabled
) {}
