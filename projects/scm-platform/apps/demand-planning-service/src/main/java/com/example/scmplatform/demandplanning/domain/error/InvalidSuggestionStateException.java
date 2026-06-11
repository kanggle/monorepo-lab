package com.example.scmplatform.demandplanning.domain.error;

/**
 * Raised when a status transition is invalid for the suggestion's current state.
 * Maps to HTTP 422 INVALID_SUGGESTION_STATE.
 */
public class InvalidSuggestionStateException extends RuntimeException {
    public InvalidSuggestionStateException(String message) {
        super(message);
    }
}
