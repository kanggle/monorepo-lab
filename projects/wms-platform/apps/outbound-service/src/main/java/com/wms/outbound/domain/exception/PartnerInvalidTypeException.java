package com.wms.outbound.domain.exception;

import java.util.UUID;

/**
 * Raised when the customer partner is not {@code ACTIVE} or its
 * {@code partner_type} is not {@code CUSTOMER} or {@code BOTH}.
 * Mapped to {@code 422} with code {@code PARTNER_INVALID_TYPE}.
 */
public class PartnerInvalidTypeException extends OutboundDomainException {

    private final UUID partnerId;

    public PartnerInvalidTypeException(UUID partnerId, String detail) {
        super("Customer partner invalid: " + partnerId + " (" + detail + ")");
        this.partnerId = partnerId;
    }

    public UUID getPartnerId() {
        return partnerId;
    }

    @Override
    public String errorCode() {
        return "PARTNER_INVALID_TYPE";
    }
}
