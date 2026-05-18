package com.example.finance.account.domain.account.status;

/**
 * Account lifecycle states (architecture.md § Account State Machine).
 *
 * <pre>
 * PENDING_KYC → ACTIVE → (RESTRICTED →) (FROZEN →) CLOSED
 * </pre>
 *
 * Only {@code ACTIVE} permits fund movement. {@code CLOSED} is terminal.
 */
public enum AccountStatus {
    PENDING_KYC,
    ACTIVE,
    RESTRICTED,
    FROZEN,
    CLOSED
}
