package com.example.finance.account.domain.error;

/**
 * Concrete fintech domain exceptions, one per code in
 * {@code specs/contracts/http/account-api.md} § Error code → HTTP. Grouped in
 * one file to keep the error vocabulary scannable; each is a distinct type so
 * the {@code GlobalExceptionHandler} can map it to the exact contract status.
 *
 * <p>Pure Java — no framework imports (domain boundary rule).
 */
public final class DomainErrors {

    private DomainErrors() {
    }

    // ---- 404 ----
    public static final class AccountNotFoundException extends FinanceDomainException {
        public AccountNotFoundException(String message) {
            super("ACCOUNT_NOT_FOUND", message);
        }
    }

    public static final class HoldNotFoundException extends FinanceDomainException {
        public HoldNotFoundException(String message) {
            super("HOLD_NOT_FOUND", message);
        }
    }

    public static final class TransactionNotFoundException extends FinanceDomainException {
        public TransactionNotFoundException(String message) {
            super("TRANSACTION_NOT_FOUND", message);
        }
    }

    // ---- 409 ----
    public static final class AccountNotActiveException extends FinanceDomainException {
        public AccountNotActiveException(String message) {
            super("ACCOUNT_NOT_ACTIVE", message);
        }
    }

    public static final class AccountFrozenException extends FinanceDomainException {
        public AccountFrozenException(String message) {
            super("ACCOUNT_FROZEN", message);
        }
    }

    public static final class AccountStatusTransitionInvalidException extends FinanceDomainException {
        public AccountStatusTransitionInvalidException(String message) {
            super("ACCOUNT_STATUS_TRANSITION_INVALID", message);
        }
    }

    public static final class HoldAlreadySettledException extends FinanceDomainException {
        public HoldAlreadySettledException(String message) {
            super("HOLD_ALREADY_SETTLED", message);
        }
    }

    public static final class TransactionStatusTransitionInvalidException extends FinanceDomainException {
        public TransactionStatusTransitionInvalidException(String message) {
            super("TRANSACTION_STATUS_TRANSITION_INVALID", message);
        }
    }

    public static final class TransactionAlreadySettledException extends FinanceDomainException {
        public TransactionAlreadySettledException(String message) {
            super("TRANSACTION_ALREADY_SETTLED", message);
        }
    }

    public static final class IdempotencyKeyConflictException extends FinanceDomainException {
        public IdempotencyKeyConflictException(String message) {
            super("IDEMPOTENCY_KEY_CONFLICT", message);
        }
    }

    // ---- 422 ----
    public static final class InsufficientAvailableBalanceException extends FinanceDomainException {
        public InsufficientAvailableBalanceException(String message) {
            super("INSUFFICIENT_AVAILABLE_BALANCE", message);
        }
    }

    public static final class CurrencyMismatchException extends FinanceDomainException {
        public CurrencyMismatchException(String message) {
            super("CURRENCY_MISMATCH", message);
        }
    }

    public static final class AmountInvalidException extends FinanceDomainException {
        public AmountInvalidException(String message) {
            super("AMOUNT_INVALID", message);
        }
    }

    public static final class AmlScreeningRequiredException extends FinanceDomainException {
        public AmlScreeningRequiredException(String message) {
            super("AML_SCREENING_REQUIRED", message);
        }
    }

    public static final class SanctionHitException extends FinanceDomainException {
        public SanctionHitException(String message) {
            super("SANCTION_HIT", message);
        }
    }

    public static final class TransactionLimitExceededException extends FinanceDomainException {
        public TransactionLimitExceededException(String message) {
            super("TRANSACTION_LIMIT_EXCEEDED", message);
        }
    }

    // ---- 403 ----
    public static final class KycRequiredException extends FinanceDomainException {
        public KycRequiredException(String message) {
            super("KYC_REQUIRED", message);
        }
    }

    public static final class KycLevelInsufficientException extends FinanceDomainException {
        public KycLevelInsufficientException(String message) {
            super("KYC_LEVEL_INSUFFICIENT", message);
        }
    }

    public static final class PermissionDeniedException extends FinanceDomainException {
        public PermissionDeniedException(String message) {
            super("PERMISSION_DENIED", message);
        }
    }

    // ---- 503 ----
    public static final class IdempotencyStoreUnavailableException extends FinanceDomainException {
        public IdempotencyStoreUnavailableException(String message) {
            super("IDEMPOTENCY_STORE_UNAVAILABLE", message);
        }
    }
}
