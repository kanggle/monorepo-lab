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

    // ---- Employee ↔ IAM account link (TASK-ERP-BE-044 / TASK-MONO-774) ----

    /** 404 — unknown account-link proposal id. */
    public static final class EmployeeLinkProposalNotFoundException extends MasterdataDomainException {
        public EmployeeLinkProposalNotFoundException(String message) {
            super("EMPLOYEE_LINK_PROPOSAL_NOT_FOUND", message);
        }
    }

    /**
     * 409 — link state collision. {@code details.cause} is one of the closed set in
     * {@code masterdata-api.md}: {@code employee_already_linked}, {@code account_already_linked},
     * {@code proposal_pending}, {@code proposal_not_pending}, {@code not_linked}.
     */
    public static final class EmployeeLinkConflictException extends MasterdataDomainException {
        public static final String EMPLOYEE_ALREADY_LINKED = "employee_already_linked";
        public static final String ACCOUNT_ALREADY_LINKED = "account_already_linked";
        public static final String PROPOSAL_PENDING = "proposal_pending";
        public static final String PROPOSAL_NOT_PENDING = "proposal_not_pending";
        public static final String NOT_LINKED = "not_linked";

        private final String cause;

        public EmployeeLinkConflictException(String cause, String message) {
            super("EMPLOYEE_LINK_CONFLICT", message, java.util.Map.of("cause", cause));
            this.cause = cause;
        }

        /** The {@code details.cause} value — named so it does not shadow {@link Throwable#getCause()}. */
        public String conflictCause() {
            return cause;
        }
    }

    /** 422 — link target not eligible ({@code details.cause = "employee_not_active"}). */
    public static final class EmployeeLinkInvalidException extends MasterdataDomainException {
        public static final String EMPLOYEE_NOT_ACTIVE = "employee_not_active";

        public EmployeeLinkInvalidException(String message) {
            super("EMPLOYEE_LINK_INVALID", message, java.util.Map.of("cause", EMPLOYEE_NOT_ACTIVE));
        }
    }

    /** 403 — accept/decline by a caller whose {@code sub} is not the proposal's account. */
    public static final class EmployeeLinkNotAddresseeException extends MasterdataDomainException {
        public EmployeeLinkNotAddresseeException(String message) {
            super("EMPLOYEE_LINK_NOT_ADDRESSEE", message);
        }
    }

    /** 403 — two-person rule: the acceptor is the proposer (or a proposal names the proposer's own account). */
    public static final class EmployeeLinkSelfAcceptException extends MasterdataDomainException {
        public EmployeeLinkSelfAcceptException(String message) {
            super("EMPLOYEE_LINK_SELF_ACCEPT", message);
        }
    }

    // ---- 503 ----
    public static final class IdempotencyStoreUnavailableException extends MasterdataDomainException {
        public IdempotencyStoreUnavailableException(String message) {
            super("IDEMPOTENCY_STORE_UNAVAILABLE", message);
        }
    }
}
