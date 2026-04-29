package com.wms.inbound.domain.exception;

import java.util.UUID;

public class PutawayLineNotFoundException extends InboundDomainException {
    public PutawayLineNotFoundException(UUID id) {
        super("PutawayLine not found: " + id);
    }

    @Override
    public String errorCode() {
        return "PUTAWAY_LINE_NOT_FOUND";
    }
}
