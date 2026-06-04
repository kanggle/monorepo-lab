package com.example.erp.approval.application.port.outbound;

import com.example.erp.approval.domain.request.ApprovalSubject;

/**
 * Outbound port for the submit-time subject reference-integrity check (E1,
 * architecture.md § Reference Integrity model). A synchronous REST call to
 * {@code masterdata-service} (ADR-MONO-005 Category B) verifies the referenced
 * master subject EXISTS and is ACTIVE before a request may leave DRAFT.
 *
 * <p>approval-service holds NO master data and never writes it back — the master
 * is reached only through this port (no shared-table JOIN even though both live
 * in the same MySQL instance).
 */
public interface MasterDataPort {

    /**
     * @return {@code true} iff the subject resolves to an EXISTING + ACTIVE
     *         master of its type. {@code false} for not-found / RETIRED /
     *         unreachable masterdata (the submit use case maps {@code false}
     *         to {@code APPROVAL_ROUTE_INVALID} — the request stays DRAFT,
     *         never advanced against a dangling master reference).
     */
    boolean isSubjectActive(ApprovalSubject subject, String tenantId);
}
