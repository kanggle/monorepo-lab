package com.example.erp.approval.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request DTOs for the delegation endpoints (approval-api.md § v2.1 amendment).
 * Bean-validation failures → 400 {@code VALIDATION_ERROR}; the domain-level
 * self-delegation / invalid-window checks → 422 {@code DELEGATION_INVALID}.
 */
public final class DelegationRequests {

    private DelegationRequests() {
    }

    /**
     * POST /delegations — create a grant. The delegator A is the caller's
     * {@code sub} (not in the body); {@code validTo} null = open-ended.
     * TASK-ERP-BE-017 — {@code scope} ({@code GLOBAL}|{@code REQUEST}, null/blank →
     * GLOBAL; an unknown value → 400 in the controller) + {@code scopeRequestId}
     * (the REQUEST-scoped grant's target request).
     */
    public record CreateDelegationRequest(
            @NotBlank @Size(max = 64) String delegateId,
            @NotNull Instant validFrom,
            Instant validTo,
            @Size(max = 512) String reason,
            @Size(max = 16) String scope,
            @Size(max = 64) String scopeRequestId) {
    }

    /** POST /delegations/{id}/revoke — reason required (audit completeness, L131). */
    public record RevokeDelegationRequest(@NotBlank @Size(max = 512) String reason) {
    }
}
