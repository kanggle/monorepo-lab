package com.example.fanplatform.artist.adapter.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Error envelope shape: {@code { code, message, details?, timestamp }}.
 * Aligns with the gateway's {@code ApiErrorEnvelope} and community-service.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorBody(String code, String message, Map<String, Object> details, Instant timestamp) {

    public static ApiErrorBody of(String code, String message) {
        return new ApiErrorBody(code, message, null, Instant.now());
    }

    public static ApiErrorBody of(String code, String message, Map<String, Object> details) {
        return new ApiErrorBody(code, message, details, Instant.now());
    }
}
