package com.example.fanplatform.community.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level success response envelope: {@code { data, meta }}. Matches the
 * shape declared in {@code specs/contracts/http/community-api.md} and is
 * consistent with the gateway's success response (which is pass-through).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(T data, Map<String, Object> meta) {

    public static <T> ApiEnvelope<T> of(T data) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("timestamp", Instant.now().toString());
        return new ApiEnvelope<>(data, meta);
    }

    public static <T> ApiEnvelope<T> of(T data, Map<String, Object> meta) {
        return new ApiEnvelope<>(data, meta);
    }
}
