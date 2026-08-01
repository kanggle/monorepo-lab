package com.example.erp.masterdata.domain.error;

/**
 * Concrete erp masterdata domain exceptions — one per code in
 * {@code specs/contracts/http/masterdata-api.md} § Error code → HTTP. Grouped
 * in one file to keep the error vocabulary scannable; each is a distinct type
 * so the {@code GlobalExceptionHandler} can map it to the exact contract status.
 *
 * <p>Pure Java — no framework imports (domain boundary rule, E layer).
 */
public final class DomainErrors {

    private DomainErrors() {
    }

    // ---- 404 ----
    public static final class MasterdataNotFoundException extends MasterdataDomainException {
        public MasterdataNotFoundException(String message) {
            super("MASTERDATA_NOT_FOUND", message);
        }
    }

    // ---- 409 ----
    public static final class MasterdataDuplicateKeyException extends MasterdataDomainException {
        public MasterdataDuplicateKeyException(String message) {
            super("MASTERDATA_DUPLICATE_KEY", message);
        }
    }

    /**
     * Retire blocked by ≥1 live referencer (E1). {@code masterdata-api.md} § retire
     * endpoints promises that {@code details} enumerates the referencer kinds, so the
     * {@code details}-carrying constructor is the one the retire guard uses:
     * {@code {"referencers": ["childDepartments", "employees", "costCenters"]}}.
     * The message-only constructor stays for the terminal-state guard
     * ({@code MasterStatusMachine}), where there is no referencer to enumerate.
     */
    public static final class MasterdataReferenceViolationException extends MasterdataDomainException {
        public MasterdataReferenceViolationException(String message) {
            super("MASTERDATA_REFERENCE_VIOLATION", message);
        }

        public MasterdataReferenceViolationException(String message,
                                                     java.util.Map<String, Object> details) {
            super("MASTERDATA_REFERENCE_VIOLATION", message, details);
        }
    }

    public static final class MasterdataParentCycleException extends MasterdataDomainException {
        public MasterdataParentCycleException(String message) {
            super("MASTERDATA_PARENT_CYCLE", message);
        }
    }

    public static final class IdempotencyKeyConflictException extends MasterdataDomainException {
        public IdempotencyKeyConflictException(String message) {
            super("IDEMPOTENCY_KEY_CONFLICT", message);
        }
    }

    public static final class ConcurrentModificationException extends MasterdataDomainException {
        public ConcurrentModificationException(String message) {
            super("CONCURRENT_MODIFICATION", message);
        }
    }

    // ---- 422 ----
    public static final class MasterdataEffectivePeriodInvalidException extends MasterdataDomainException {
        public MasterdataEffectivePeriodInvalidException(String message) {
            super("MASTERDATA_EFFECTIVE_PERIOD_INVALID", message);
        }
    }

    // ---- 403 ----
    public static final class PermissionDeniedException extends MasterdataDomainException {
        public PermissionDeniedException(String message) {
            super("PERMISSION_DENIED", message);
        }
    }

    public static final class DataScopeForbiddenException extends MasterdataDomainException {
        public DataScopeForbiddenException(String message) {
            super("DATA_SCOPE_FORBIDDEN", message);
        }
    }

    public static final class TenantForbiddenException extends MasterdataDomainException {
        public TenantForbiddenException(String message) {
            super("TENANT_FORBIDDEN", message);
        }
    }

    public static final class ExternalTrafficRejectedException extends MasterdataDomainException {
        public ExternalTrafficRejectedException(String message) {
            super("EXTERNAL_TRAFFIC_REJECTED", message);
        }
    }

    // ---- 503 ----
    public static final class IdempotencyStoreUnavailableException extends MasterdataDomainException {
        public IdempotencyStoreUnavailableException(String message) {
            super("IDEMPOTENCY_STORE_UNAVAILABLE", message);
        }
    }
}
