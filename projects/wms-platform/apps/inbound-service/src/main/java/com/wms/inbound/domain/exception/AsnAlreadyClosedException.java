package com.wms.inbound.domain.exception;

import java.util.UUID;

public class AsnAlreadyClosedException extends InboundDomainException {
    public AsnAlreadyClosedException(UUID asnId, String status) {
        super("ASN " + asnId + " is already in terminal status: " + status);
    }

    @Override
    public String errorCode() {
        return "ASN_ALREADY_CLOSED";
    }
}
