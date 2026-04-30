package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UnlockAccountRequest(
        @NotBlank(message = "Reason is required")
        String reason,

        String operatorId,
        String ticketId
) {
}
