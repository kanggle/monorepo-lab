package com.wms.inbound.domain.exception;

import java.util.UUID;

public class PutawayInstructionNotFoundException extends InboundDomainException {
    public PutawayInstructionNotFoundException(UUID id) {
        super("PutawayInstruction not found: " + id);
    }

    @Override
    public String errorCode() {
        return "PUTAWAY_INSTRUCTION_NOT_FOUND";
    }
}
