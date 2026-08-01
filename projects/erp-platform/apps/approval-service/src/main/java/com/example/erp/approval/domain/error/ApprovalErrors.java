package com.example.erp.approval.domain.error;

/**
 * Concrete erp approval domain exceptions — one per code in
 * {@code specs/contracts/http/approval-api.md} § Error code → HTTP. Grouped in
 * one file to keep the error vocabulary scannable; each is a distinct type so
 * the {@code GlobalExceptionHandler} can map it to the exact contract status.
 *
 * <p>The 5 approval-specific codes ({@code APPROVAL_REQUEST_NOT_FOUND},
 * {@code APPROVAL_STATUS_TRANSITION_INVALID}, {@code APPROVAL_NOT_AUTHORIZED_APPROVER},
 * {@code APPROVAL_ROUTE_INVALID}, {@code APPROVAL_ALREADY_FINALIZED}) are defined
 * verbatim in {@code rules/domains/erp.md} § Standard Error Codes → Approval
 * Workflow. The remaining codes are the shared platform/authorization codes.
 *
 * <p>Pure Java — no framework imports (domain boundary rule, E layer).
 */
public final class ApprovalErrors {

    private ApprovalErrors() {
    }

    // ---- 404 ----
    public static final class ApprovalRequestNotFoundException extends ApprovalDomainException {
        public ApprovalRequestNotFoundException(String message) {
            super("APPROVAL_REQUEST_NOT_FOUND", message);
        }
    }

    // ---- 409 (transition from a state that is not the legal predecessor) ----
    public static final class ApprovalStatusTransitionInvalidException extends ApprovalDomainException {
        public ApprovalStatusTransitionInvalidException(String message) {
            super("APPROVAL_STATUS_TRANSITION_INVALID", message);
        }
    }

    // ---- 409 (transition attempted on a terminal request) ----
    public static final class ApprovalAlreadyFinalizedException extends ApprovalDomainException {
        public ApprovalAlreadyFinalizedException(String message) {
            super("APPROVAL_ALREADY_FINALIZED", message);
        }
    }

    // ---- 403 (acting principal is not the route's approver / submitter) ----

    /**
     * {@code approval-api.md} § withdraw reuses this code for the submitter-only
     * constraint and discriminates it with {@code details.role = "submitter"} — hence
     * the {@code details}-carrying constructor. Approver-position rejections carry no
     * {@code details} (the contract documents none for them).
     */
    public static final class ApprovalNotAuthorizedApproverException extends ApprovalDomainException {

        /** {@code details.role} value the contract documents for the submitter-only gate. */
        public static final java.util.Map<String, Object> ROLE_SUBMITTER =
                java.util.Map.of("role", "submitter");

        public ApprovalNotAuthorizedApproverException(String message) {
            super("APPROVAL_NOT_AUTHORIZED_APPROVER", message);
        }

        public ApprovalNotAuthorizedApproverException(String message,
                                                      java.util.Map<String, Object> details) {
            super("APPROVAL_NOT_AUTHORIZED_APPROVER", message, details);
        }
    }

    // ---- 422 (route malformed: no approver / self-approval / subject unresolved) ----

    /**
     * {@code approval-api.md} documents three {@code details.cause} discriminators for
     * this single code — {@code subject_unresolved}, {@code self_approval},
     * {@code duplicate_stage_approver} ("**No new error code** — the existing approval
     * codes cover it"). Structural route defects the contract does not name (empty
     * stage list, blank approver, stage index out of range) carry no {@code details};
     * the field is documented as optional (`<object?>`), so omitting it there is
     * contract-conformant and avoids inventing wire values.
     */
    public static final class ApprovalRouteInvalidException extends ApprovalDomainException {

        public static final String CAUSE_SUBJECT_UNRESOLVED = "subject_unresolved";
        public static final String CAUSE_SELF_APPROVAL = "self_approval";
        public static final String CAUSE_DUPLICATE_STAGE_APPROVER = "duplicate_stage_approver";

        public ApprovalRouteInvalidException(String message) {
            super("APPROVAL_ROUTE_INVALID", message);
        }

        public ApprovalRouteInvalidException(String message,
                                             java.util.Map<String, Object> details) {
            super("APPROVAL_ROUTE_INVALID", message, details);
        }

        /** Convenience for the three contract-documented {@code details.cause} values. */
        public static ApprovalRouteInvalidException withCause(String cause, String message) {
            return new ApprovalRouteInvalidException(message, java.util.Map.of("cause", cause));
        }
    }

    // ---- 409 (same key, different payload) ----
    public static final class IdempotencyKeyConflictException extends ApprovalDomainException {
        public IdempotencyKeyConflictException(String message) {
            super("IDEMPOTENCY_KEY_CONFLICT", message);
        }
    }

    // ---- 409 (optimistic-lock conflict) ----
    public static final class ConcurrentModificationException extends ApprovalDomainException {
        public ConcurrentModificationException(String message) {
            super("CONCURRENT_MODIFICATION", message);
        }
    }

    // ---- 400 (reject / withdraw without reason, validation) ----
    public static final class ValidationException extends ApprovalDomainException {
        public ValidationException(String message) {
            super("VALIDATION_ERROR", message);
        }
    }

    // ---- 403 (required role not present) ----
    public static final class PermissionDeniedException extends ApprovalDomainException {
        public PermissionDeniedException(String message) {
            super("PERMISSION_DENIED", message);
        }
    }

    // ---- 403 (subject outside caller data scope) ----
    public static final class DataScopeForbiddenException extends ApprovalDomainException {
        public DataScopeForbiddenException(String message) {
            super("DATA_SCOPE_FORBIDDEN", message);
        }
    }

    // ---- 503 (idempotency store unreachable, fail-closed) ----
    public static final class IdempotencyStoreUnavailableException extends ApprovalDomainException {
        public IdempotencyStoreUnavailableException(String message) {
            super("IDEMPOTENCY_STORE_UNAVAILABLE", message);
        }
    }

    // ---- 422 (delegation grant malformed: self-delegation / invalid window) ----
    // TASK-ERP-BE-013 (대결/위임). error-handling.md § Approval Workflow [domain: erp].
    public static final class DelegationInvalidException extends ApprovalDomainException {
        public DelegationInvalidException(String message) {
            super("DELEGATION_INVALID", message);
        }
    }

    // ---- 404 (delegation grant not found for the caller's tenant) ----
    public static final class DelegationNotFoundException extends ApprovalDomainException {
        public DelegationNotFoundException(String message) {
            super("DELEGATION_NOT_FOUND", message);
        }
    }
}
