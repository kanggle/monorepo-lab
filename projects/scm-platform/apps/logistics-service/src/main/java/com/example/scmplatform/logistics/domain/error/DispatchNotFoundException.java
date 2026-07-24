package com.example.scmplatform.logistics.domain.error;

import java.util.UUID;

/**
 * Raised when an operator inspects or retries a dispatch that does not exist — either by
 * dispatch id or by the shipment id it dispatches. Maps to HTTP 404 {@code DISPATCH_NOT_FOUND}
 * at the web edge.
 */
public class DispatchNotFoundException extends RuntimeException {

    public DispatchNotFoundException(UUID id) {
        super("Dispatch not found: " + id);
    }

    private DispatchNotFoundException(String message) {
        super(message);
    }

    /** No dispatch exists for the given shipment (the seam event has not arrived, or wms never shipped it). */
    public static DispatchNotFoundException forShipment(UUID shipmentId) {
        return new DispatchNotFoundException("Dispatch not found for shipment: " + shipmentId);
    }
}
