package com.wms.outbound.domain.exception;

import java.util.UUID;

/**
 * Raised when a {@code PickingRequest} id cannot be located. Mapped to
 * {@code 404} with code {@code PICKING_REQUEST_NOT_FOUND}.
 */
public class PickingRequestNotFoundException extends OutboundDomainException {

    private final UUID pickingRequestId;

    public PickingRequestNotFoundException(UUID pickingRequestId) {
        super("Picking request not found: " + pickingRequestId);
        this.pickingRequestId = pickingRequestId;
    }

    public UUID getPickingRequestId() {
        return pickingRequestId;
    }

    @Override
    public String errorCode() {
        return "PICKING_REQUEST_NOT_FOUND";
    }
}
