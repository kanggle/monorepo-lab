package com.example.finance.account.domain.transaction;

/**
 * Fund-movement transaction kind (architecture.md § Transaction State
 * Machine). REVERSAL is the only correction path for a settled txn (F3).
 */
public enum TransactionType {
    TOPUP,
    WITHDRAW,
    TRANSFER,
    HOLD,
    CAPTURE,
    RELEASE,
    REVERSAL
}
