package com.example.scmplatform.procurement.domain.po.repository;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;

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
     * Search with optional filters. {@code status} and {@code supplierId}
     * may be null. Paging + sort are carried by the framework-free {@link PageQuery};
     * infrastructure adapters convert it to the JPA paging types at the boundary.
     */
    PageResult<PurchaseOrder> search(String tenantId, PoStatus status, String supplierId, PageQuery pageQuery);
}
