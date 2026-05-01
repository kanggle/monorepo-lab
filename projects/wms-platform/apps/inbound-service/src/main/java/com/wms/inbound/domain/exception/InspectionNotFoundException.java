package com.wms.inbound.domain.exception;

import java.util.UUID;

public class InspectionNotFoundException extends InboundDomainException {
    public InspectionNotFoundException(UUID id) {
        super("Inspection not found: " + id);
    }

    public InspectionNotFoundException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "INSPECTION_NOT_FOUND";
    }
}
