package com.example.finance.ledger.domain.error;

/**
 * Concrete fintech domain exceptions, one per code in
 * {@code specs/contracts/http/ledger-api.md} § Error codes. Grouped in one file
 * to keep the error vocabulary scannable; each is a distinct type so the
 * {@code GlobalExceptionHandler} can map it to the exact contract status.
 *
 * <p>Pure Java — no framework imports (domain boundary rule).
 */
public final class LedgerErrors {

    private LedgerErrors() {
    }

    // ---- 404 ----
    public static final class JournalEntryNotFoundException extends LedgerDomainException {
        public JournalEntryNotFoundException(String message) {
            super("JOURNAL_ENTRY_NOT_FOUND", message);
        }
    }

    public static final class LedgerAccountNotFoundException extends LedgerDomainException {
        public LedgerAccountNotFoundException(String message) {
            super("LEDGER_ACCOUNT_NOT_FOUND", message);
        }
    }

    // ---- 422 ----
    /**
     * The balance identity {@code Σ debit == Σ credit} is violated. Thrown by the
     * {@code JournalEntry} factory — defense in depth so an unbalanced set can
     * never be persisted (architecture.md § Posting Policy, F2).
     */
    public static final class LedgerEntryUnbalancedException extends LedgerDomainException {
        public LedgerEntryUnbalancedException(String message) {
            super("LEDGER_ENTRY_UNBALANCED", message);
        }
    }

    public static final class CurrencyMismatchException extends LedgerDomainException {
        public CurrencyMismatchException(String message) {
            super("CURRENCY_MISMATCH", message);
        }
    }
}
