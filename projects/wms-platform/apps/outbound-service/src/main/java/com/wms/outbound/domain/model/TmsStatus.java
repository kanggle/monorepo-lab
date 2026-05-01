package com.wms.outbound.domain.model;

/**
 * TMS (Transportation Management System) notification outcome state on
 * {@link Shipment}.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §5 and
 * {@code specs/services/outbound-service/external-integrations.md} §2.10.
 */
public enum TmsStatus {
    /** Shipment created; TMS push not yet attempted (or in flight). */
    PENDING,
    /** TMS acknowledged the shipment notification. */
    NOTIFIED,
    /** TMS push exhausted retries / circuit / bulkhead. */
    NOTIFY_FAILED
}
