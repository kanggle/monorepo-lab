package com.example.admin.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record BulkLockResponse(List<ResultItem> results) {

    public record ResultItem(
            String accountId,
            String outcome,
            @JsonInclude(JsonInclude.Include.NON_NULL) ErrorDetail error
    ) {}

    public record ErrorDetail(String code, String message) {}
}
