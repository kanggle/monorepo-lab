package com.wms.admin.domain.error;

public final class StateTransitionInvalidException extends AdminDomainException {
    public StateTransitionInvalidException(String detail) {
        super("STATE_TRANSITION_INVALID", detail);
    }
}
