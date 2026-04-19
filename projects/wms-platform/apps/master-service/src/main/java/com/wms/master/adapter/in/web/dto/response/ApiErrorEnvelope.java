package com.wms.master.adapter.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Error envelope matching
 * {@code specs/contracts/http/master-service-api.md §Error Envelope}.
 */
public record ApiErrorEnvelope(ApiError error) {

    public static ApiErrorEnvelope of(String code, String message) {
        return new ApiErrorEnvelope(new ApiError(code, message, null, null, null));
    }

    public static ApiErrorEnvelope of(String code, String message, Map<String, Object> details) {
        return new ApiErrorEnvelope(new ApiError(code, message, details, null, null));
    }

    public static ApiErrorEnvelope of(String code, String message, Map<String, Object> details,
                                      String traceId, String requestId) {
        return new ApiErrorEnvelope(new ApiError(code, message, details, traceId, requestId));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(
            String code,
            String message,
            Map<String, Object> details,
            String traceId,
            String requestId) {
    }
}
