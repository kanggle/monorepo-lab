package com.wms.inbound.domain.exception;

public class InspectionIncompleteException extends InboundDomainException {
    public InspectionIncompleteException(int unacknowledgedCount) {
        super("Inspection has " + unacknowledgedCount + " unacknowledged discrepancies");
    }

    @Override
    public String errorCode() {
        return "INSPECTION_INCOMPLETE";
    }
}
