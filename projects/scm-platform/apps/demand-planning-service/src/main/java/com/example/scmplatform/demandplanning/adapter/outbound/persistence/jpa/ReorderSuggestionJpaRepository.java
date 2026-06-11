package com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa;

import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReorderSuggestionJpaRepository
        extends JpaRepository<ReorderSuggestionJpaEntity, UUID> {

    /**
     * Open-suggestion guard (D6): check if a SUGGESTED or APPROVED suggestion
     * exists for (tenantId, skuCode, warehouseId).
     */
    @Query("SELECT COUNT(s) > 0 FROM ReorderSuggestionJpaEntity s " +
           "WHERE s.tenantId = :tenantId AND s.skuCode = :skuCode " +
           "AND s.warehouseId = :warehouseId AND s.status IN :openStatuses")
    boolean hasOpenSuggestion(@Param("tenantId") String tenantId,
                               @Param("skuCode") String skuCode,
                               @Param("warehouseId") UUID warehouseId,
                               @Param("openStatuses") List<SuggestionStatus> openStatuses);

    Page<ReorderSuggestionJpaEntity> findByTenantIdAndStatusAndSkuCode(
            String tenantId, SuggestionStatus status, String skuCode, Pageable pageable);

    Page<ReorderSuggestionJpaEntity> findByTenantIdAndStatus(
            String tenantId, SuggestionStatus status, Pageable pageable);

    Page<ReorderSuggestionJpaEntity> findByTenantIdAndSkuCode(
            String tenantId, String skuCode, Pageable pageable);

    Page<ReorderSuggestionJpaEntity> findByTenantId(String tenantId, Pageable pageable);

    Optional<ReorderSuggestionJpaEntity> findByIdAndTenantId(UUID id, String tenantId);
}
