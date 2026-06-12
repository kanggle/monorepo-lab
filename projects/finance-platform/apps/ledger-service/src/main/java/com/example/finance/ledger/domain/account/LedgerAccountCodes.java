package com.example.finance.ledger.domain.account;

/**
 * Canonical ledger-account codes (architecture.md § Chart of Accounts). The two
 * platform GL accounts are fixed constants; per-customer wallet codes are derived
 * by {@link #customerWallet(String)} ({@code CUSTOMER_WALLET:{accountId}}, created
 * lazily on first posting). Pure Java.
 */
public final class LedgerAccountCodes {

    public static final String CASH_CLEARING = "CASH_CLEARING";
    public static final String SETTLEMENT_SUSPENSE = "SETTLEMENT_SUSPENSE";
    public static final String CUSTOMER_WALLET_PREFIX = "CUSTOMER_WALLET:";

    private LedgerAccountCodes() {
    }

    /** The per-customer wallet liability account code for an account id. */
    public static String customerWallet(String accountId) {
        return CUSTOMER_WALLET_PREFIX + accountId;
    }

    /** True for a {@code CUSTOMER_WALLET:{accountId}} code. */
    public static boolean isCustomerWallet(String code) {
        return code != null && code.startsWith(CUSTOMER_WALLET_PREFIX);
    }

    /** The natural account type for a code (wallets = LIABILITY, GL accounts = ASSET). */
    public static LedgerAccountType typeForCode(String code) {
        if (isCustomerWallet(code)) {
            return LedgerAccountType.LIABILITY;
        }
        return LedgerAccountType.ASSET;
    }
}
