package com.example.erp.readmodel.domain.approval;

/**
 * The subject kind an approval request targets (read-only projection, E5). The
 * subject's department (for the {@code org_scope} read filter) is resolved at
 * read time: a {@link #DEPARTMENT} subject is its own department; an
 * {@link #EMPLOYEE} subject resolves its department via {@code employee_proj}.
 */
public enum ApprovalSubjectType {
    DEPARTMENT,
    EMPLOYEE;

    /** Parses a subject type, returning {@code null} when unknown/absent. */
    public static ApprovalSubjectType fromOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
