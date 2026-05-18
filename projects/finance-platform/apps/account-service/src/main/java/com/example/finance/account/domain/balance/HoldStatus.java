package com.example.finance.account.domain.balance;

/**
 * Hold lifecycle (architecture.md § Balance Model). A hold reserves available
 * funds; it is settled by capture or release (or auto-released on expiry).
 * CAPTURED / RELEASED / EXPIRED are terminal — re-settling raises
 * {@code HOLD_ALREADY_SETTLED}.
 */
public enum HoldStatus {
    ACTIVE,
    CAPTURED,
    RELEASED,
    EXPIRED;

    public boolean isSettled() {
        return this != ACTIVE;
    }
}
