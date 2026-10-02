package com.example.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TASK-BE-618 — body of {@code POST /internal/auth/consumer-pool/moves} (auth-internal.md).
 *
 * @param accountId    the account whose credential moves into the consumer pool
 * @param siteTenantId the consumer site the account lives in now; the credential must be in it
 */
public record ConsumerPoolMoveRequest(
        @NotBlank @Size(max = 36) String accountId,
        @NotBlank @Size(max = 32) String siteTenantId
) {
}
