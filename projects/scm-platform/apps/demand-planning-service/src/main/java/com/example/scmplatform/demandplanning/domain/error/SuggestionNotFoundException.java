package com.example.scmplatform.demandplanning.domain.error;

import java.util.UUID;

/**
 * Raised when a reorder suggestion is not found. Maps to HTTP 404 SUGGESTION_NOT_FOUND.
 */
public class SuggestionNotFoundException extends RuntimeException {
    public SuggestionNotFoundException(UUID id) {
        super("Reorder suggestion not found: " + id);
    }
}
