package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * TASK-BE-374 (ADR-MONO-034 U4 / U6 step 3d): request body for
 * {@code POST /internal/tenants/{tenantId}/identities:resolveOrCreate}.
 *
 * <p>{@code reuseExisting} drives the no-silent-merge decision (ADR-034 U3): when
 * an identity already exists for the (tenant, email), it is only handed back when
 * {@code reuseExisting=true}. A {@code null} flag is treated as {@code false}
 * (conservative default — never reuse without an explicit opt-in). The email is
 * format-validated here and re-validated/normalized (lowercase) by the {@code Email}
 * value object in the use case.
 */
public record ResolveOrCreateIdentityRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        Boolean reuseExisting
) {
    /** Null-safe opt-in: a missing flag is a conservative {@code false} (no reuse). */
    public boolean reuseExistingOrFalse() {
        return Boolean.TRUE.equals(reuseExisting);
    }
}
