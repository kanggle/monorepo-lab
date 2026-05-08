package com.wms.admin.application.port;

import java.time.Instant;

/**
 * Cached response for an idempotent request. {@code bodyJson} is the raw
 * UTF-8 response body. Replay sends {@code bodyJson} verbatim with
 * {@code status} + {@code contentType}.
 */
public record StoredResponse(
        String requestHash,
        int status,
        String bodyJson,
        String contentType,
        Instant createdAt) {
}
