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

    // ---- Reconciliation (4th increment — TASK-FIN-BE-010, F8) ----

    /**
     * An ingest targeted a ledger account that is not a reconcilable clearing
     * account — only {@code CASH_CLEARING} / {@code SETTLEMENT_SUSPENSE} reconcile
     * against an external statement (architecture.md § Reconciliation). 422 —
     * reconciling a wallet account would mis-classify its movements as discrepancies.
     */
    public static final class ReconciliationAccountInvalidException extends LedgerDomainException {
        public ReconciliationAccountInvalidException(String message) {
            super("RECONCILIATION_ACCOUNT_INVALID", message);
        }
    }

    /** A statement id unknown / not in the tenant on read. 404. */
    public static final class ReconciliationStatementNotFoundException extends LedgerDomainException {
        public ReconciliationStatementNotFoundException(String message) {
            super("RECONCILIATION_STATEMENT_NOT_FOUND", message);
        }
    }

    /** A discrepancy id unknown / not in the tenant on read or resolve. 404. */
    public static final class ReconciliationDiscrepancyNotFoundException extends LedgerDomainException {
        public ReconciliationDiscrepancyNotFoundException(String message) {
            super("RECONCILIATION_DISCREPANCY_NOT_FOUND", message);
        }
    }

    /**
     * A resolve attempted on an already-RESOLVED discrepancy — the OPEN→RESOLVED
     * transition is one-way (operator-only, never auto; fintech F8). 409.
     */
    public static final class ReconciliationAlreadyResolvedException extends LedgerDomainException {
        public ReconciliationAlreadyResolvedException(String message) {
            super("RECONCILIATION_ALREADY_RESOLVED", message);
        }
    }

    // ---- Reconciliation period lock (6th increment — TASK-FIN-BE-012, F8) ----

    /**
     * A resolve attempted on a discrepancy whose owning statement's
     * {@code statementDate} falls in a CLOSED accounting period — the closed month's
     * reconciliation is frozen with the books (architecture.md § Reconciliation
     * § Period lock; the analog of {@code LEDGER_PERIOD_CLOSED}). 422; correct via
     * the next (open) period. Net-zero when no covering CLOSED period / no statement.
     */
    public static final class ReconciliationPeriodLockedException extends LedgerDomainException {
        public ReconciliationPeriodLockedException(String message) {
            super("RECONCILIATION_PERIOD_LOCKED", message);
        }
    }

    // ---- Manual journal posting (5th increment — TASK-FIN-BE-011, F1) ----

    /**
     * A manual {@code POST /entries} arrived without a client {@code Idempotency-Key}
     * (or with a blank / oversized key). 400 — the idempotency key is required so a
     * double-submit is replay-safe (architecture.md § Manual Journal Posting,
     * fintech F1). The key must be ≤ 50 chars (so {@code "manual:" + key} fits the
     * 64-char {@code source_event_id} column).
     */
    public static final class IdempotencyKeyRequiredException extends LedgerDomainException {
        public IdempotencyKeyRequiredException(String message) {
            super("IDEMPOTENCY_KEY_REQUIRED", message);
        }
    }
}
