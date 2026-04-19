package com.wms.master.application.port.out;

import java.time.Instant;

/**
 * Cached response for an idempotent request.
 * <p>
 * {@code bodyJson} is the raw response body as UTF-8 text. When the
 * {@code Idempotency-Key} is replayed, the filter writes {@code bodyJson} back
 * to the HTTP response verbatim with {@code status} and {@code contentType}.
 */
public record StoredResponse(
        String requestHash,
        int status,
        String bodyJson,
        String contentType,
        Instant createdAt) {
}
