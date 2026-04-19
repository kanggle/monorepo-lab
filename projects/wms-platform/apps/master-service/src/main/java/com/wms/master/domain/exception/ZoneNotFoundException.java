package com.wms.master.domain.exception;

public class ZoneNotFoundException extends MasterDomainException {

    public ZoneNotFoundException(String identifier) {
        super("ZONE_NOT_FOUND", "Zone not found: " + identifier);
    }
}
