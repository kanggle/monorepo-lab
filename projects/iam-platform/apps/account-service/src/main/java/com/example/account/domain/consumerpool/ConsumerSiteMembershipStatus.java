package com.example.account.domain.consumerpool;

/**
 * TASK-BE-614 — {@code consumer_site_memberships.status} (account-service data-model.md).
 *
 * <p>{@link #LEFT} is leaving one site; it is not deleting the account (the account's own
 * lifecycle stays in {@code accounts.status}).
 *
 * <p>{@link #LOCKED} (TASK-BE-621, owner decision 2026-10-04 «사이트 운영자의 잠금은 자기 사이트에만») is
 * that site's operator locking the person out of THAT site only — not locking the account. It admits no
 * token for the site, consent does not reopen it, and the site's operator unlocking it makes it
 * {@link #ACTIVE} again.
 */
public enum ConsumerSiteMembershipStatus {
    ACTIVE,
    LEFT,
    LOCKED
}
