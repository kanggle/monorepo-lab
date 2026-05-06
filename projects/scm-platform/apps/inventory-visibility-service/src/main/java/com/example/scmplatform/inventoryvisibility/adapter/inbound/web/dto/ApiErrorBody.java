package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorBody(String code, String message, Map<String, Object> details, String timestamp) {

    public static ApiErrorBody of(String code, String message) {
        return new ApiErrorBody(code, message, null, Instant.now().toString());
    }

    public static ApiErrorBody of(String code, String message, Map<String, Object> details) {
        return new ApiErrorBody(code, message, details, Instant.now().toString());
    }
}
