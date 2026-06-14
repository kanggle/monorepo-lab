package com.example.admin.application.exception;

/**
 * TASK-BE-373 / ADR-MONO-034 U3 — raised by the link operation when the operator
 * is already linked to a DIFFERENT central identity than the one requested.
 *
 * <p>Re-linking to the SAME identity is a no-op success (idempotent), NOT this
 * exception. Linking when already bound to a different identity is rejected: the
 * caller must explicitly {@code unlink} first (U6 reversibility), preventing a
 * silent re-point of an established link. Surfaces as
 * {@code 409 OPERATOR_ALREADY_LINKED}.
 */
public class OperatorAlreadyLinkedException extends RuntimeException {
    public OperatorAlreadyLinkedException(String message) {
        super(message);
    }
}
