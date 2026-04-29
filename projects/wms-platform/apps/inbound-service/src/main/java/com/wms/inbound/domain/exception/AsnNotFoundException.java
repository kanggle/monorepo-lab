package com.wms.inbound.domain.exception;

import java.util.UUID;

public class AsnNotFoundException extends InboundDomainException {
    public AsnNotFoundException(UUID id) {
        super("ASN not found: " + id);
    }

    public AsnNotFoundException(String asnNo) {
        super("ASN not found: " + asnNo);
    }

    @Override
    public String errorCode() {
        return "ASN_NOT_FOUND";
    }
}
