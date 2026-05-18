package com.example.finance.account.domain.account;

/**
 * KYC tier (architecture.md § KYC/AML Compliance Gate). Higher ordinal = more
 * verified. Per-level fund limits live in {@code compliance/KycGate} — this
 * enum only carries the tier identity + a relative ordering helper.
 */
public enum KycLevel {
    NONE,
    BASIC,
    FULL;

    /** True when {@code this} tier is at least {@code required}. */
    public boolean isAtLeast(KycLevel required) {
        return this.ordinal() >= required.ordinal();
    }
}
