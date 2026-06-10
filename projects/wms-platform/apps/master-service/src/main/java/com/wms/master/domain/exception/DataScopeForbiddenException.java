package com.wms.master.domain.exception;

/**
 * TASK-MONO-215 (ADR-MONO-025 § 3.3 step 2 — first ABAC data-scope extension):
 * raised when a deliberately data-scoped operator targets a warehouse outside its
 * {@code data_scope}/{@code org_scope} set. Maps to 403 {@code DATA_SCOPE_FORBIDDEN}.
 *
 * <p>Net-zero: an unrestricted ({@code "*"}) or unscoped (no scope claim — base /
 * machine tokens) operator is NEVER confined; only a deliberately-scoped operator
 * can hit this. See {@code platform/abac-data-scope.md} +
 * {@link com.example.security.jwt.AbacDataScope}.
 */
public class DataScopeForbiddenException extends MasterDomainException {

    public DataScopeForbiddenException(String message) {
        super("DATA_SCOPE_FORBIDDEN", message);
    }
}
