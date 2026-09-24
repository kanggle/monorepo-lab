package com.wms.outbound.domain.exception;

import java.util.UUID;

/**
 * Raised when a pick confirmation's {@code actualLocationId} resolves to a location that
 * is not {@code ACTIVE}. Mapped to {@code 422} with code {@code LOCATION_INACTIVE} — the
 * same code inbound uses for a putaway target (TASK-BE-596).
 */
public class LocationInactiveException extends OutboundDomainException {

    public LocationInactiveException(UUID locationId) {
        super("location is not ACTIVE: " + locationId);
    }

    @Override
    public String errorCode() {
        return "LOCATION_INACTIVE";
    }
}
