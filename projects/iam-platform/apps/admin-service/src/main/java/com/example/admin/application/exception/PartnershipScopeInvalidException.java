package com.example.admin.application.exception;

/**
 * TASK-BE-477 / ADR-MONO-045 D3 — an invite's {@code delegatedScope} violates the
 * cap: it carries an admin role ({@code SUPER_ADMIN}/{@code TENANT_ADMIN}/
 * {@code TENANT_BILLING_ADMIN}) or exceeds what the host itself holds (≤-own across
 * the org boundary). Maps to HTTP 422 {@code PARTNERSHIP_SCOPE_INVALID}.
 */
public class PartnershipScopeInvalidException extends RuntimeException {

    public PartnershipScopeInvalidException(String message) {
        super(message);
    }
}
