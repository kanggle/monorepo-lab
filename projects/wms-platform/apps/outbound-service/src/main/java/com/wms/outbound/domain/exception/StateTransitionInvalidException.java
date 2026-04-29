package com.wms.outbound.domain.exception;

/**
 * Raised when a domain state transition is attempted from a state that
 * does not allow it. Mapped to {@code 422} with code
 * {@code STATE_TRANSITION_INVALID}.
 */
public class StateTransitionInvalidException extends OutboundDomainException {

    private final String fromState;
    private final String toState;

    public StateTransitionInvalidException(String fromState, String toState) {
        super("Invalid transition: " + fromState + " → " + toState);
        this.fromState = fromState;
        this.toState = toState;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    @Override
    public String errorCode() {
        return "STATE_TRANSITION_INVALID";
    }
}
