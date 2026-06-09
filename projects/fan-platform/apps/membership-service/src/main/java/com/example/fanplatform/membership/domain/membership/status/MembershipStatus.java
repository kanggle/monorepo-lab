package com.example.fanplatform.membership.domain.membership.status;

/**
 * Stored membership lifecycle state. {@code CANCELED} is terminal. There is no
 * stored {@code EXPIRED} — expiry is evaluated at read time (architecture.md §
 * State Machine).
 */
public enum MembershipStatus {
    ACTIVE,
    CANCELED
}
