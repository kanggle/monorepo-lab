package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * TASK-MONO-772 S2 — body of {@code POST /internal/notifications/operator-invitation} (admin-to-account.md).
 *
 * <p>🔴 {@code token} is the raw invitation token. {@code toString} redacts it (and masks the recipient) so that no
 * log line printing the request can leak it; validation messages are fixed strings that never echo a value.
 */
public record OperatorInvitationMailRequest(

        @NotBlank(message = "to must not be blank")
        @Email(message = "to must be an email address")
        @Size(max = 320, message = "to must be at most 320 characters")
        String to,

        @NotBlank(message = "token must not be blank")
        @Size(max = 128, message = "token must be at most 128 characters")
        String token,

        @NotBlank(message = "tenantId must not be blank")
        @Size(max = 32, message = "tenantId must be at most 32 characters")
        String tenantId,

        @Size(max = 120, message = "inviterDisplayName must be at most 120 characters")
        String inviterDisplayName,

        @NotNull(message = "expiresAt is required")
        Instant expiresAt
) {
    @Override
    public String toString() {
        return "OperatorInvitationMailRequest[to=<masked>, token=<redacted>, tenantId=" + tenantId
                + ", expiresAt=" + expiresAt + "]";
    }
}
