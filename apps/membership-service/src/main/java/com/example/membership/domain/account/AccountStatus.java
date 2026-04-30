package com.example.membership.domain.account;

/**
 * Account status as observed from account-service. Kept locally as a separate
 * enum to preserve bounded-context isolation.
 */
public enum AccountStatus {
    ACTIVE,
    LOCKED,
    DORMANT,
    DELETED
}
