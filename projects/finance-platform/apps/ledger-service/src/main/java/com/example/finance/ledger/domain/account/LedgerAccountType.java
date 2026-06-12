package com.example.finance.ledger.domain.account;

/**
 * Chart-of-accounts node type with its natural balance side (architecture.md
 * § Chart of Accounts). The first increment uses only ASSET / LIABILITY;
 * EQUITY / INCOME / EXPENSE are reserved for later increments. Pure Java.
 */
public enum LedgerAccountType {
    ASSET(NormalSide.DEBIT),
    LIABILITY(NormalSide.CREDIT),
    EQUITY(NormalSide.CREDIT),
    INCOME(NormalSide.CREDIT),
    EXPENSE(NormalSide.DEBIT);

    private final NormalSide normalSide;

    LedgerAccountType(NormalSide normalSide) {
        this.normalSide = normalSide;
    }

    public NormalSide normalSide() {
        return normalSide;
    }
}
