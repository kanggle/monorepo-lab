package com.wms.inbound.domain.exception;

import java.util.UUID;

public class PutawayQuantityExceededException extends InboundDomainException {
    public PutawayQuantityExceededException(UUID asnLineId, int qtyToPutaway, int qtyPassed) {
        super("Putaway quantity " + qtyToPutaway + " exceeds inspection-passed " + qtyPassed
                + " for asnLineId=" + asnLineId);
    }

    @Override
    public String errorCode() {
        return "PUTAWAY_QUANTITY_EXCEEDED";
    }
}
