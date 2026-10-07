package com.example.erp.masterdata.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request bodies for the employee ↔ IAM account link endpoints (TASK-ERP-BE-044,
 * masterdata-api.md § Employee ↔ IAM account link). Accept takes {@code {}} and has no DTO.
 */
public final class AccountLinkRequests {

    private AccountLinkRequests() {
    }

    /**
     * {@code accountId} is checked for FORM only (blank / over 64 chars → 400
     * {@code VALIDATION_ERROR}) — its existence in IAM is deliberately not checked
     * (owner decision 2026-10-08 UTC (b)).
     */
    public record ProposeAccountLinkRequest(
            @NotBlank @Size(max = 64) String accountId,
            @Size(max = 256) String reason) {
    }

    public record DeclineAccountLinkRequest(
            @Size(max = 256) String reason) {
    }

    public record RevokeAccountLinkRequest(
            @NotBlank @Size(max = 256) String reason) {
    }

    public record UnlinkAccountRequest(
            @NotBlank @Size(max = 256) String reason) {
    }
}
