package com.wms.master.domain.exception;

public class LocationNotFoundException extends MasterDomainException {

    public LocationNotFoundException(String identifier) {
        super("LOCATION_NOT_FOUND", "Location not found: " + identifier);
    }
}
