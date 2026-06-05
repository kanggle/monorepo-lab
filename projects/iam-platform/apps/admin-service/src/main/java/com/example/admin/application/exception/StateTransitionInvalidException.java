package com.example.admin.application.exception;

/**
 * Raised when an operator status change request targets the same state as the
 * current operator row (e.g. ACTIVE → ACTIVE). Surfaces as
 * {@code 400 STATE_TRANSITION_INVALID}.
 */
public class StateTransitionInvalidException extends RuntimeException {
    public StateTransitionInvalidException(String message) {
        super(message);
    }
}
