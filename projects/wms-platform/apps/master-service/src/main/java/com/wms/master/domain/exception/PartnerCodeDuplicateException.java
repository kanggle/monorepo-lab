package com.wms.master.domain.exception;

public class PartnerCodeDuplicateException extends MasterDomainException {

    public PartnerCodeDuplicateException(String partnerCode) {
        super("PARTNER_CODE_DUPLICATE", "partnerCode is already taken: " + partnerCode);
    }
}
