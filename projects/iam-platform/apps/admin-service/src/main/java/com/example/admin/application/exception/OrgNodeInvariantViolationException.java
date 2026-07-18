package com.example.admin.application.exception;

/**
 * TASK-BE-492 (ADR-MONO-047 D2/D4) — a 422 from the account-service org-node authority,
 * surfaced to the operator <b>unchanged</b>.
 *
 * <p>admin-service is a thin command gateway: it does NOT re-implement the tree invariants
 * (cycle, {@code depth ≤ 5}, {@code child.ceiling ⊆ parent}, delete-only-when-empty). It
 * carries the authority's error code through so a duplicated check can never drift from
 * the enforcing one.
 *
 * <p>{@code code} ∈ {{@code ORG_NODE_CYCLE}, {@code ORG_NODE_DEPTH_EXCEEDED},
 * {@code ORG_NODE_CEILING_NOT_SUBSET}, {@code ORG_NODE_NOT_EMPTY}}.
 */
public class OrgNodeInvariantViolationException extends AccountBusinessException {

    private static final String FALLBACK_CODE = "ORG_NODE_INVARIANT_VIOLATION";

    private final String code;

    public OrgNodeInvariantViolationException(String code, String message) {
        super(message);
        this.code = (code == null || code.isBlank()) ? FALLBACK_CODE : code;
    }

    /** The account-service error code, passed through to the operator response body. */
    public String getCode() {
        return code;
    }
}
