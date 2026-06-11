package com.example.scmplatform.demandplanning.adapter.inbound.web.dto;

/**
 * Standard scm error body { code, message }.
 */
public record ApiErrorBody(String code, String message) {

    public static ApiErrorBody of(String code, String message) {
        return new ApiErrorBody(code, message);
    }
}
