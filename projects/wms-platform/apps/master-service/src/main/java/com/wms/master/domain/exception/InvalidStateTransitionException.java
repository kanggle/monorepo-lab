package com.wms.master.domain.exception;

public class InvalidStateTransitionException extends MasterDomainException {

    public InvalidStateTransitionException(String currentState, String requestedTransition) {
        super(
            "STATE_TRANSITION_INVALID",
            "Cannot " + requestedTransition + " from state " + currentState
        );
    }

    /**
     * Free-form reason constructor used when the invariant being violated is not a
     * simple "from state X" transition — for example, parent-aggregate inactivity
     * or the presence of active child aggregates. Same error code / HTTP status as
     * the state-only overload.
     */
    public InvalidStateTransitionException(String reason) {
        super("STATE_TRANSITION_INVALID", reason);
    }
}
