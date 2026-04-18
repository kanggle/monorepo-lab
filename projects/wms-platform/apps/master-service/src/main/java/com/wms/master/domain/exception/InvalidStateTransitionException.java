package com.wms.master.domain.exception;

public class InvalidStateTransitionException extends MasterDomainException {

    public InvalidStateTransitionException(String currentState, String requestedTransition) {
        super(
            "STATE_TRANSITION_INVALID",
            "Cannot " + requestedTransition + " from state " + currentState
        );
    }
}
