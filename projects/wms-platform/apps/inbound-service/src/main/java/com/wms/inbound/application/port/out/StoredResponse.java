package com.wms.inbound.application.port.out;

import java.time.Instant;

/**
 * Cached response for a successfully-completed idempotent request.
 *
 * <p>{@code bodyJson} is the raw response body as UTF-8 text — written back
 * verbatim on a replay alongside {@code status} and {@code contentType}.
 *
 * <p>{@code requestHash} is the SHA-256 of the canonicalized request body.
 * Mismatch on replay means the same {@code Idempotency-Key} was used with a
 * different payload, which the filter surfaces as
 * {@code DUPLICATE_REQUEST (409)}.
 */
public record StoredResponse(
        String requestHash,
        int status,
        String bodyJson,
        String contentType,
        Instant createdAt) {
}
