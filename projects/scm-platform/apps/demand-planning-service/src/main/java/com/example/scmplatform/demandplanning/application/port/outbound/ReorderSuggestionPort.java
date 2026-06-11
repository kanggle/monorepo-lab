package com.example.scmplatform.demandplanning.application.port.outbound;

import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    Page<ReorderSuggestion> findAll(String tenantId, SuggestionStatus status,
                                    String skuCode, Pageable pageable);
}
