package com.wms.inbound.domain.exception;

import java.util.UUID;

public class LocationInactiveException extends InboundDomainException {
    public LocationInactiveException(UUID locationId) {
        super("Location is inactive or not found in read-model: " + locationId);
    }

    @Override
    public String errorCode() {
        return "LOCATION_INACTIVE";
    }
}
