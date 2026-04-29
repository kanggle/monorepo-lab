package com.wms.inbound.domain.exception;

import java.util.UUID;

public class InspectionQuantityMismatchException extends InboundDomainException {
    public InspectionQuantityMismatchException(UUID asnLineId, int expected, int actual) {
        super("Inspection quantity exceeds expected for line " + asnLineId
                + ": expected=" + expected + " actual=" + actual);
    }

    @Override
    public String errorCode() {
        return "INSPECTION_QUANTITY_MISMATCH";
    }
}
