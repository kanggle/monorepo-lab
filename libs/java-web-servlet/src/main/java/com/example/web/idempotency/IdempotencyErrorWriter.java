package com.example.web.idempotency;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Writes the idempotency error responses, keeping the HTTP error envelope
 * service-owned (ADR-MONO-038 I4). Each service implements this with its own
 * {@code ApiErrorEnvelope} so {@link IdempotencyKeyFilter} imposes no shared
 * error DTO.
 */
public interface IdempotencyErrorWriter {

    /**
     * Writes 409 {@code DUPLICATE_REQUEST} — the Idempotency-Key was already
     * used with a different request body.
     */
    void writeConflict(HttpServletResponse response) throws IOException;

    /**
     * Writes 503 {@code PROCESSING} (with a {@code Retry-After} header) — a
     * concurrent request for the same key holds the lock.
     */
    void writeProcessing(HttpServletResponse response) throws IOException;

    /**
     * Writes 400 for an over-length Idempotency-Key. Only invoked when the
     * filter is configured with a positive {@code maxKeyLength}; the default
     * throws so that configuring a key-length guard without a matching writer
     * fails loudly rather than silently skipping the 400.
     */
    default void writeKeyTooLong(HttpServletResponse response, int maxKeyLength) throws IOException {
        throw new IllegalStateException(
                "maxKeyLength is configured but this IdempotencyErrorWriter does not implement writeKeyTooLong");
    }
}
