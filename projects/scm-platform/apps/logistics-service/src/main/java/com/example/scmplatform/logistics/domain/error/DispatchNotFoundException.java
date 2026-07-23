package com.example.scmplatform.logistics.domain.error;

import java.util.UUID;

/**
 * Raised when an operator inspects or retries a dispatch id that does not exist.
 * Maps to HTTP 404 {@code DISPATCH_NOT_FOUND} at the web edge.
 */
public class DispatchNotFoundException extends RuntimeException {

    public DispatchNotFoundException(UUID id) {
        super("Dispatch not found: " + id);
    }
}
