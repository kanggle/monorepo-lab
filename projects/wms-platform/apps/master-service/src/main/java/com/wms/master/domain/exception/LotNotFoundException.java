package com.wms.master.domain.exception;

public class LotNotFoundException extends MasterDomainException {

    public LotNotFoundException(String identifier) {
        super("LOT_NOT_FOUND", "Lot not found: " + identifier);
    }
}
