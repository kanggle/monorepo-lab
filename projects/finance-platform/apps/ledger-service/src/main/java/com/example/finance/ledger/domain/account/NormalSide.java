package com.example.finance.ledger.domain.account;

/**
 * The side on which a ledger account's balance naturally increases
 * (architecture.md § Chart of Accounts). An ASSET grows on the DEBIT side; a
 * LIABILITY grows on the CREDIT side. Pure Java.
 */
public enum NormalSide {
    DEBIT,
    CREDIT
}
