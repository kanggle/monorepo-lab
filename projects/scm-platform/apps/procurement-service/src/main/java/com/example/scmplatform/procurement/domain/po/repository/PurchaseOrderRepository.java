package com.example.scmplatform.procurement.domain.po.repository;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;

import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port for {@link PurchaseOrder} persistence.
 *
 * <p>Multi-tenant: every read is scoped by {@code tenantId}. Cross-tenant
 * misuse is reported as {@code Optional.empty()} (NOT 403) so callers cannot
 * differentiate "PO does not exist" from "PO belongs to another tenant"
 * (Edge Case #5).
 */
public interface PurchaseOrderRepository {

    PurchaseOrder save(PurchaseOrder po);

    Optional<PurchaseOrder> findById(String id, String tenantId);

    Optional<PurchaseOrder> findByPoNumber(String poNumber, String tenantId);

    /**
     * Find the PO previously materialized from the given reorder suggestion
     * (ADR-MONO-027 D5 cross-service idempotency). Tenant-scoped.
     */
    Optional<PurchaseOrder> findBySourceSuggestionId(String sourceSuggestionId, String tenantId);

    /**
     * Search with optional filters. {@code status}, {@code excludeStatus}, {@code supplierId},
     * {@code submittedFrom} and {@code submittedTo} may be null. {@code excludeStatus} drops POs
     * currently in that status (TASK-MONO-810). The two instants are a
     * half-open {@code [submittedFrom, submittedTo)} on {@code submitted_at}; when either is
     * given, a PO never submitted (no {@code submitted_at}) is excluded (TASK-MONO-810).
     * Paging + sort are carried by the framework-free {@link PageQuery};
     * infrastructure adapters convert it to the JPA paging types at the boundary.
     */
    PageResult<PurchaseOrder> search(String tenantId, PoStatus status, PoStatus excludeStatus, String supplierId,
                                     Instant submittedFrom, Instant submittedTo, PageQuery pageQuery);
}
