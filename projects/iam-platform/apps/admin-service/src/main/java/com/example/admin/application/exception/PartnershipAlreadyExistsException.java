package com.example.admin.application.exception;

/**
 * TASK-BE-477 / ADR-MONO-045 — a partnership already exists for the ordered
 * {@code (host_tenant_id, partner_tenant_id)} pair ({@code uk_tenant_partnership_pair}).
 * Maps to HTTP 409 {@code PARTNERSHIP_ALREADY_EXISTS}.
 */
public class PartnershipAlreadyExistsException extends RuntimeException {

    public PartnershipAlreadyExistsException(String message) {
        super(message);
    }
}
