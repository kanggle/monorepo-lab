package com.wms.inventory.domain.exception;

/**
 * Attempted state transition violates the aggregate's state machine (e.g.,
 * confirming a reservation that's already {@code RELEASED}). Maps to
 * {@code 422 STATE_TRANSITION_INVALID}.
 */
public class StateTransitionInvalidException extends InventoryDomainException {

    public StateTransitionInvalidException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "STATE_TRANSITION_INVALID";
    }
}
