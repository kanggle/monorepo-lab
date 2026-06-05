package com.example.account.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * TASK-BE-257: Request DTO for POST /internal/tenants/{tenantId}/accounts:bulk.
 *
 * <p>Top-level {@code @Size(max=1000)} is the Bean Validation gate; the use case
 * also enforces this limit so the error code ({@code BULK_LIMIT_EXCEEDED}) is
 * returned consistently whether validation fires at the controller boundary or
 * the application layer.
 */
public record BulkProvisionAccountRequest(

        @NotNull(message = "items must not be null")
        @Size(max = 1000, message = "items must not exceed 1000 entries")
        @Valid
        List<BulkProvisionAccountItem> items

) {

    /**
     * Per-row item in the bulk request.
     *
     * @param externalId  caller-side dedup key (optional)
     * @param email       required; must be valid email format
     * @param phone       optional phone number
     * @param displayName optional; max 100 chars
     * @param roles       optional; each role must match {@code ^[A-Z][A-Z0-9_]*$}, max 64 chars
     * @param status      optional; {@code ACTIVE} or {@code DORMANT} (default {@code ACTIVE})
     */
    public record BulkProvisionAccountItem(

            @Size(max = 64, message = "externalId must be at most 64 characters")
            String externalId,

            @NotNull(message = "email is required")
            @Email(message = "email must be a valid email address")
            String email,

            @Size(max = 32, message = "phone must be at most 32 characters")
            String phone,

            @Size(max = 100, message = "displayName must be at most 100 characters")
            String displayName,

            List<@Size(max = 64, message = "each role must be at most 64 characters")
            @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
                    message = "each role must match ^[A-Z][A-Z0-9_]*$") String> roles,

            @Pattern(regexp = "^(ACTIVE|DORMANT)$",
                    message = "status must be ACTIVE or DORMANT")
            String status

    ) {}
}
