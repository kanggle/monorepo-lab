package com.example.membership.domain.account;

/**
 * Port: checks whether an account is eligible for subscription operations.
 * Implementations must be fail-closed: circuit open / communication error must
 * throw {@link com.example.membership.application.exception.AccountStatusUnavailableException}.
 */
public interface AccountStatusChecker {

    AccountStatus check(String accountId);
}
