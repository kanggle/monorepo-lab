package com.example.payment.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PaymentCreateRequest(
        @NotBlank String orderId,
        @NotBlank String userId,
        @Positive long amount
) {
}
