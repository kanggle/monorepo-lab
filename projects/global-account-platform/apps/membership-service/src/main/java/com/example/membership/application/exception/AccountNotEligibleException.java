package com.example.membership.application.exception;

import com.example.membership.domain.account.AccountStatus;

public class AccountNotEligibleException extends RuntimeException {

    private final AccountStatus status;

    public AccountNotEligibleException(AccountStatus status) {
        super("Account not eligible for subscription: status=" + status);
        this.status = status;
    }

    public AccountStatus getStatus() {
        return status;
    }
}
