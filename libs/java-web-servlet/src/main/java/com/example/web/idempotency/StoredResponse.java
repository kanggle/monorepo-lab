package com.example.web.idempotency;

import java.time.Instant;

/**
 * A cached HTTP response stored against an Idempotency-Key, used by
 * {@link IdempotencyKeyFilter} to replay the original response when the same
 * key + body is retried.
 *
 * <p>Project-agnostic value type — the per-service idempotency stores persist
 * and return this record.
 *
 * @param requestHash the canonical body hash the response was produced for
 *                    (so a same-key/different-body retry is detected as a 409)
 * @param status      the HTTP status of the cached response (always 2xx)
 * @param bodyJson    the cached response body (may be empty, never null when stored)
 * @param contentType the cached response content type (defaults applied by the filter when null)
 * @param storedAt    when the entry was stored
 */
public record StoredResponse(
        String requestHash,
        int status,
        String bodyJson,
        String contentType,
        Instant storedAt) {
}
