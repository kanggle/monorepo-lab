package com.wms.master.domain.exception;

/**
 * Raised when a caller tries to create (or persist) a Location whose
 * {@code locationCode} already exists anywhere in the system. The unique
 * constraint is global (W3), not scoped to warehouse or zone.
 */
public class LocationCodeDuplicateException extends MasterDomainException {

    public LocationCodeDuplicateException(String locationCode) {
        super(
                "LOCATION_CODE_DUPLICATE",
                "locationCode '" + locationCode + "' is already taken");
    }
}
