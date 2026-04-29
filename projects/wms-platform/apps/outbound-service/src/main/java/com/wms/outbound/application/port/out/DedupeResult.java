package com.wms.outbound.application.port.out;

/**
 * Outcome of a webhook dedupe insert attempt.
 *
 * <p>Mirrors the fields used by {@code ErpOrderWebhookController} when
 * mapping the persistence-adapter response to a 200 status payload.
 */
public enum DedupeResult {
    PROCESSED,
    IGNORED_DUPLICATE
}
