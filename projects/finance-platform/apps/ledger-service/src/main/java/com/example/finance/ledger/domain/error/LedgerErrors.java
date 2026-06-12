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

    // ---- Accounting period (2nd increment — TASK-FIN-BE-008) ----

    /**
     * A journal entry whose {@code postedAt} is covered by a CLOSED accounting
     * period — the books are locked for that window (architecture.md § Accounting
     * Period § Posting guard, F2). 422; on the consumer path the event routes to
     * the DLT (no dedupe row written). Net-zero when no closed period covers it.
     */
    public static final class LedgerPeriodClosedException extends LedgerDomainException {
        public LedgerPeriodClosedException(String message) {
            super("LEDGER_PERIOD_CLOSED", message);
        }
    }

    public static final class AccountingPeriodNotFoundException extends LedgerDomainException {
        public AccountingPeriodNotFoundException(String message) {
            super("ACCOUNTING_PERIOD_NOT_FOUND", message);
        }
    }

    /**
     * An opened window overlaps an existing period for the tenant — would make
     * "which period owns this entry" ambiguous (architecture.md § Non-overlap
     * invariant, F2). 422.
     */
    public static final class AccountingPeriodOverlapException extends LedgerDomainException {
        public AccountingPeriodOverlapException(String message) {
            super("ACCOUNTING_PERIOD_OVERLAP", message);
        }
    }

    /** A close attempted on an already-CLOSED period (no reopen). 409. */
    public static final class AccountingPeriodAlreadyClosedException extends LedgerDomainException {
        public AccountingPeriodAlreadyClosedException(String message) {
            super("ACCOUNTING_PERIOD_ALREADY_CLOSED", message);
        }
    }

    /** An opened window with {@code from ≥ to} (the half-open window is empty). 422. */
    public static final class AccountingPeriodInvalidWindowException extends LedgerDomainException {
        public AccountingPeriodInvalidWindowException(String message) {
            super("ACCOUNTING_PERIOD_INVALID_WINDOW", message);
        }
    }
}
