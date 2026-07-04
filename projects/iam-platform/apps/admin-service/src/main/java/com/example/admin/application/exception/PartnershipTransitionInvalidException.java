package com.example.admin.application.exception;

/**
 * TASK-BE-477 / ADR-MONO-045 — a partnership lifecycle transition violates the
 * state matrix (e.g. accept on a non-PENDING partnership, reactivate on a
 * non-SUSPENDED one). Maps to HTTP 409 {@code PARTNERSHIP_TRANSITION_INVALID}.
 */
public class PartnershipTransitionInvalidException extends RuntimeException {

    public PartnershipTransitionInvalidException(String message) {
        super(message);
    }
}
