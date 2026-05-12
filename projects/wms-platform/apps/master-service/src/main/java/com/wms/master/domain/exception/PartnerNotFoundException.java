package com.wms.master.domain.exception;

public class PartnerNotFoundException extends MasterDomainException {

    public PartnerNotFoundException(String identifier) {
        super("PARTNER_NOT_FOUND", "Partner not found: " + identifier);
    }
}
