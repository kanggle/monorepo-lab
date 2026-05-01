package com.wms.outbound.domain.exception;

import java.util.UUID;

/**
 * Raised when a {@code Shipment} id cannot be located. Mapped to {@code 404}
 * with code {@code SHIPMENT_NOT_FOUND}.
 */
public class ShipmentNotFoundException extends OutboundDomainException {

    private final UUID shipmentId;

    public ShipmentNotFoundException(UUID shipmentId) {
        super("Shipment not found: " + shipmentId);
        this.shipmentId = shipmentId;
    }

    public UUID getShipmentId() {
        return shipmentId;
    }

    @Override
    public String errorCode() {
        return "SHIPMENT_NOT_FOUND";
    }
}
