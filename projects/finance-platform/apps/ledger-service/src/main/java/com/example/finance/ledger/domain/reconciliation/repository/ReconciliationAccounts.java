package com.example.finance.ledger.domain.reconciliation.repository;

import com.example.finance.ledger.domain.account.LedgerAccountCodes;

/**
 * The clearing-account allow-list — which ledger account codes are reconcilable
 * against an external statement (architecture.md § Reconciliation). Only the two
 * platform clearing accounts ({@code CASH_CLEARING} / {@code SETTLEMENT_SUSPENSE})
 * face an external bank / PG and may be reconciled; a wallet account is NOT
 * reconcilable (reconciling it would mis-classify its movements as discrepancies
 * → {@code RECONCILIATION_ACCOUNT_INVALID}). Pure Java — no Spring/JPA.
 */
public final class ReconciliationAccounts {

    private ReconciliationAccounts() {
    }

    /** True only for {@code CASH_CLEARING} / {@code SETTLEMENT_SUSPENSE}. */
    public static boolean isReconcilable(String code) {
        return LedgerAccountCodes.CASH_CLEARING.equals(code)
                || LedgerAccountCodes.SETTLEMENT_SUSPENSE.equals(code);
    }
}
