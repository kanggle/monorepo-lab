package com.example.erp.masterdata.domain.authorization;

/**
 * Requirement a use case asks the authorization port for (v1 surface — per-aggregate matrix is forward-decl for
 * v2 `permission-service`, architecture.md § Authorization matrix + Data scope). What each value opens is the
 * erp iam-integration.md § 업무 역할 table (TASK-MONO-793 · ADR-MONO-083 P6).
 */
public enum RequiredScope {
    /** Masterdata read — {@code ERP_MASTERDATA_READ ∨ ERP_MASTERDATA_WRITE ∨ erp.read ∨ erp.write ∨ tenant '*'}. */
    READ,
    /** Masterdata change — {@code ERP_MASTERDATA_WRITE ∨ erp.write}. */
    WRITE,
    /**
     * The approval subject check ({@code GET /departments/{id}} · {@code GET /employees/{id}}) — {@link #READ}
     * {@code ∨ ERP_APPROVAL_WRITE} (owner decision Q3): approval-service forwards the operator's token here at submit.
     */
    SUBJECT_READ,
    /**
     * The employee picker ({@code GET /employees/picker}, TASK-ERP-BE-045) — the SAME role predicate as
     * {@link #SUBJECT_READ} ({@link #READ} {@code ∨ ERP_APPROVAL_WRITE}; {@code ERP_APPROVAL_READ} alone is refused).
     * A separate value rather than reusing {@code SUBJECT_READ}: the matrix is declared per use case
     * (architecture.md § Authorization matrix), and this use case differs from the subject GETs in what it returns
     * (a scoped LIST, not one row checked against its department) — reading {@code SUBJECT_READ} at a list call
     * site would hide that. The adapter maps both values onto one arm, so they cannot drift apart.
     */
    PICKER,
    /**
     * A personal endpoint — the caller's own employee record, link proposals addressed to them, an approver
     * reference (owner decisions Q2 · Q3): any ERP business role or the derived entry role, or an erp scope, or
     * tenant {@code '*'}.
     */
    SELF
}
