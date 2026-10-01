package com.example.account.domain.consumerpool;

/**
 * TASK-BE-614 — {@code consumer_site_memberships.status} (account-service data-model.md).
 *
 * <p>{@link #LEFT} is leaving one site; it is not deleting the account (the account's own
 * lifecycle stays in {@code accounts.status}).
 */
public enum ConsumerSiteMembershipStatus {
    ACTIVE,
    LEFT
}
