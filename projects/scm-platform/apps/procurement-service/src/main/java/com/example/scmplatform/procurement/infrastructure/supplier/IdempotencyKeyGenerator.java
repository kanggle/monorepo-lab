package com.example.scmplatform.procurement.infrastructure.supplier;

import com.example.common.id.UuidV7;
import org.springframework.stereotype.Component;

/**
 * Generates per-attempt idempotency keys for outbound supplier calls (S2).
 * UUID v7 keeps keys time-ordered which simplifies supplier-side dedupe
 * window queries.
 */
@Component
public class IdempotencyKeyGenerator {

    /**
     * Build an idempotency key bound to {@code (poId, attemptToken)}. The
     * supplier dedupes on the full key, so calling with a stable key
     * (e.g. the same client-supplied {@code Idempotency-Key} header from
     * the buyer's request) keeps PO submission idempotent across retries.
     */
    public String forSubmission(String poId, String attemptToken) {
        if (attemptToken != null && !attemptToken.isBlank()) {
            return "po-" + poId + "-" + attemptToken;
        }
        return "po-" + poId + "-" + UuidV7.randomString();
    }
}
