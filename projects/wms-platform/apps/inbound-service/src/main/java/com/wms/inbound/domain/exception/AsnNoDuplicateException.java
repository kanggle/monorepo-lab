package com.wms.inbound.domain.exception;

public class AsnNoDuplicateException extends InboundDomainException {
    public AsnNoDuplicateException(String asnNo) {
        super("ASN number already exists: " + asnNo);
    }

    @Override
    public String errorCode() {
        return "ASN_NO_DUPLICATE";
    }
}
