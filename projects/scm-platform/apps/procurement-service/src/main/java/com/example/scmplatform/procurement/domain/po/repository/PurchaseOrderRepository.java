package com.example.scmplatform.procurement.domain.po.repository;

import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
     * Search with optional filters. {@code status} and {@code supplierId}
     * may be null. Results are ordered by createdAt DESC.
     */
    Page<PurchaseOrder> search(String tenantId, PoStatus status, String supplierId, Pageable pageable);
}
