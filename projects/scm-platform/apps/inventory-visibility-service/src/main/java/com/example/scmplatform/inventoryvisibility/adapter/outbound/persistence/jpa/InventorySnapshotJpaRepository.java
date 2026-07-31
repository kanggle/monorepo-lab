package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventorySnapshotJpaRepository extends JpaRepository<InventorySnapshotJpaEntity, String> {

    Optional<InventorySnapshotJpaEntity> findByNodeIdAndSkuAndTenantId(
            String nodeId, String sku, String tenantId);

    List<InventorySnapshotJpaEntity> findByNodeIdAndTenantId(String nodeId, String tenantId);

    List<InventorySnapshotJpaEntity> findBySkuAndTenantId(String sku, String tenantId);

    /**
     * Cross-node paginated search. Returning {@link Page} (rather than {@code List}) makes
     * Spring Data issue the matching {@code COUNT} query itself — replaces the previous
     * separate {@code findAllByTenantId}/{@code countByTenantId} pair (ADR-MONO-058 § D3 /
     * TASK-SCM-BE-056).
     */
    @Query("SELECT s FROM InventorySnapshotJpaEntity s WHERE s.tenantId = :tenantId ORDER BY s.updatedAt DESC")
    Page<InventorySnapshotJpaEntity> search(@Param("tenantId") String tenantId, Pageable pageable);
}
