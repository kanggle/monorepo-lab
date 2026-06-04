package com.example.erp.approval.domain.request;

import java.util.Objects;

/**
 * The single master subject an approval request is about (E1, architecture.md
 * § Reference Integrity model): {@code (subjectType ∈ {DEPARTMENT, EMPLOYEE},
 * subjectId)}. The {@code MasterDataPort} verifies it exists and is ACTIVE in
 * {@code masterdata-service} before the request may leave DRAFT.
 *
 * <p>Pure value object — no framework imports.
 */
public record ApprovalSubject(SubjectType subjectType, String subjectId) {

    public ApprovalSubject {
        Objects.requireNonNull(subjectType, "subjectType");
        Objects.requireNonNull(subjectId, "subjectId");
        if (subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId must not be blank");
        }
    }
}
