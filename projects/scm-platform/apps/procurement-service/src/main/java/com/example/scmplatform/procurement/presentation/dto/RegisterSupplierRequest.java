package com.example.scmplatform.procurement.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * {@code POST /api/procurement/suppliers} body, per
 * {@code procurement-api.md} § POST /api/procurement/suppliers.
 *
 * <p>🔴 <b>Do not add a credential field.</b> v1 accepts supplier credentials on
 * no path; that is deferred whole to the v2 {@code supplier-service}
 * (ADR-SCM-001 ACCEPT rider), and the contract states it explicitly.
 */
public record RegisterSupplierRequest(

        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]*",
                message = "code must match [A-Z0-9][A-Z0-9_-]*")
        String code,

        @NotBlank
        @Size(max = 200)
        String name,

        Instant contractStartedAt,

        Instant contractExpiresAt
) {
}
