package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GdprDeleteRequest(
        @NotBlank(message = "Reason is required")
        String reason,

        @NotBlank(message = "Operator ID is required")
        String operatorId
) {
}
