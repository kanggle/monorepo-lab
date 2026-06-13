package com.example.settlement.domain.model;

/**
 * The kind of a {@code commission_accrual} row. The ledger is append-only — a
 * correction is never an in-place update but a new {@link #REVERSAL} row that
 * negates a prior {@link #ACCRUAL} (F3, ledger-style immutability).
 */
public enum AccrualType {
    /** A positive booking from a {@code PaymentCompleted} (money captured). */
    ACCRUAL,
    /** The negative of a prior accrual, from a {@code PaymentRefunded} (full reversal). */
    REVERSAL
}
