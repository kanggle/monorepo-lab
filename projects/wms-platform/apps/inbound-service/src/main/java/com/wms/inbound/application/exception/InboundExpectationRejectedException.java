package com.wms.inbound.application.exception;

/**
 * Fail-closed rejection of a consumed scm inbound-expected event (ADR-MONO-050 D3/D4):
 * unknown/inactive destination warehouse, a non-{@code WMS_WAREHOUSE} destination node type,
 * an unknown/inactive supplier or SKU, or a structurally invalid payload.
 *
 * <p>Extends {@link IllegalArgumentException} so the shared {@code DefaultErrorHandler}
 * (see {@code KafkaConsumerConfig}) classifies it as <strong>non-retryable</strong> — the
 * failure is deterministic, so the record goes straight to {@code <topic>.DLT} rather than
 * burning retry attempts. The consumer logs an ops signal alongside the throw.
 */
public class InboundExpectationRejectedException extends IllegalArgumentException {

    public InboundExpectationRejectedException(String message) {
        super(message);
    }
}
