package com.example.finance.account.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Wire form of money (account-api.md): {@code { "amount": "<integer-minor-
 * units>", "currency": "<ISO-4217>" }}. {@code amount} is a STRING-encoded
 * integer in minor units — never a JSON float/decimal (F5). The
 * {@code @Pattern} rejects any non-integer (decimal point / float) at the
 * binding boundary.
 */
public record MoneyDto(
        @NotBlank(message = "amount is required")
        @Pattern(regexp = "^-?\\d+$", message = "amount must be an integer minor-unit string")
        String amount,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a 3-letter ISO-4217 code")
        String currency) {
}
