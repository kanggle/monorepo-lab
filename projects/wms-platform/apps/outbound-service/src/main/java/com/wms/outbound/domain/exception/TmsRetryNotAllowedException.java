package com.wms.outbound.domain.exception;

import java.util.UUID;

/**
 * Raised when {@code POST /shipments/{id}:retry-tms-notify} is invoked on a
 * shipment whose TMS status is not {@code NOTIFY_FAILED} (and therefore the
 * caller cannot recover anything).
 *
 * <p>Maps to 422 {@code STATE_TRANSITION_INVALID} per
 * {@code outbound-service-api.md} §4.3.
 */
public class TmsRetryNotAllowedException extends OutboundDomainException {

    private final UUID shipmentId;
    private final String currentStatus;

    public TmsRetryNotAllowedException(UUID shipmentId, String currentStatus) {
        super("TMS retry not allowed for shipment " + shipmentId
                + " in tms_status=" + currentStatus + " (must be NOTIFY_FAILED)");
        this.shipmentId = shipmentId;
        this.currentStatus = currentStatus;
    }

    public UUID getShipmentId() {
        return shipmentId;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    @Override
    public String errorCode() {
        return "STATE_TRANSITION_INVALID";
    }
}
