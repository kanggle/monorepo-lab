package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseOrderJpaRepository extends JpaRepository<PurchaseOrder, String> {

    Optional<PurchaseOrder> findByIdAndTenantId(String id, String tenantId);

    Optional<PurchaseOrder> findByPoNumberAndTenantId(String poNumber, String tenantId);

    Optional<PurchaseOrder> findBySourceSuggestionIdAndTenantId(String sourceSuggestionId, String tenantId);

    @Query("""
            SELECT p FROM PurchaseOrder p
            WHERE p.tenantId = :tenantId
              AND (:status IS NULL OR p.status = :status)
              AND (:supplierId IS NULL OR p.supplierId = :supplierId)
            ORDER BY p.createdAt DESC
            """)
    Page<PurchaseOrder> search(@Param("tenantId") String tenantId,
                               @Param("status") PoStatus status,
                               @Param("supplierId") String supplierId,
                               Pageable pageable);
}
