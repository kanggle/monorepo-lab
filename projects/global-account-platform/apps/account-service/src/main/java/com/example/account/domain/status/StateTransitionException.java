package com.example.account.domain.status;

public class StateTransitionException extends RuntimeException {

    private final AccountStatus from;
    private final AccountStatus to;
    private final StatusChangeReason reason;

    public StateTransitionException(AccountStatus from, AccountStatus to, StatusChangeReason reason) {
        super(String.format("State transition from %s to %s with reason %s is not allowed", from, to, reason));
        this.from = from;
        this.to = to;
        this.reason = reason;
    }

    public AccountStatus getFrom() {
        return from;
    }

    public AccountStatus getTo() {
        return to;
    }

    public StatusChangeReason getReason() {
        return reason;
    }
}
