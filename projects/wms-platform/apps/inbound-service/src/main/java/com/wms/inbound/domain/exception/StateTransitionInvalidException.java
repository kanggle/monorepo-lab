package com.wms.inbound.domain.exception;

public class StateTransitionInvalidException extends InboundDomainException {
    public StateTransitionInvalidException(String from, String to) {
        super("Invalid state transition: " + from + " -> " + to);
    }

    public StateTransitionInvalidException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "STATE_TRANSITION_INVALID";
    }
}
