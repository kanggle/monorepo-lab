package com.wms.inbound.domain.exception;

import java.util.UUID;

public class PartnerInvalidTypeException extends InboundDomainException {
    public PartnerInvalidTypeException(UUID partnerId, String reason) {
        super("Partner " + partnerId + " is not valid for ASN: " + reason);
    }

    @Override
    public String errorCode() {
        return "PARTNER_INVALID_TYPE";
    }
}
