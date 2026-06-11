package com.example.scmplatform.demandplanning.adapter.inbound.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard scm { data, meta } response envelope.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(T data, Object meta) {

    public static <T> ApiEnvelope<T> of(T data) {
        return new ApiEnvelope<>(data, null);
    }

    public static <T> ApiEnvelope<T> of(T data, Object meta) {
        return new ApiEnvelope<>(data, meta);
    }
}
