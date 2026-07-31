package com.example.scmplatform.demandplanning.application.port.outbound;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for reorder suggestion persistence.
 */
public interface ReorderSuggestionPort {

    Optional<ReorderSuggestion> findById(UUID id);

    /**
     * Check if an open (SUGGESTED or APPROVED) suggestion exists for the given
     * (tenantId, skuCode, warehouseId) tuple — the open-suggestion guard (D6).
     */
    boolean hasOpenSuggestion(String tenantId, String skuCode, UUID warehouseId);

    ReorderSuggestion save(ReorderSuggestion suggestion);

    /**
     * Cross-status/SKU paginated search, sourced from the shared
     * {@link PageQuery}/{@link PageResult} carrier (ADR-MONO-058 § D3 / TASK-SCM-BE-056
     * — mirrors {@code procurement-service}'s {@code PurchaseOrderRepository.search}
     * pattern). Replaces the previous direct {@code Pageable}/{@code Page<ReorderSuggestion>}
     * leak of Spring Data types across this port boundary.
     */
    PageResult<ReorderSuggestion> findAll(String tenantId, SuggestionStatus status,
                                          String skuCode, PageQuery pageQuery);
}
