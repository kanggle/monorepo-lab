package com.example.admin.application.exception;

/**
 * TASK-BE-477 / ADR-MONO-045 — the {@code partnershipId} does not exist, OR the
 * acting-side tenant ({@code X-Tenant-Id}) is not a party (host/partner) to the
 * partnership for the requested operation (enumeration-safe: a legit
 * {@code TENANT_ADMIN} of an unrelated tenant cannot tell the partnership exists).
 * Maps to HTTP 404 {@code PARTNERSHIP_NOT_FOUND}.
 */
public class PartnershipNotFoundException extends RuntimeException {

    public PartnershipNotFoundException(String message) {
        super(message);
    }
}
