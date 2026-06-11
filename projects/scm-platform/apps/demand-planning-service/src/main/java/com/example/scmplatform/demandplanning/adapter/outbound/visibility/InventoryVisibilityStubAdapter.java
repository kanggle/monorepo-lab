package com.example.scmplatform.demandplanning.adapter.outbound.visibility;

import com.example.scmplatform.demandplanning.application.port.outbound.InventoryVisibilityPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stub implementation of {@link InventoryVisibilityPort} for Phase 1 (BE-024).
 * The live cross-service REST client is deferred to a follow-up task.
 * Returns empty list so the sweep batch degrades gracefully without a live IVS connection.
 *
 * <p>BE-025 / follow-up: replace this stub with a RestClient that calls
 * {@code GET /api/inventory-visibility/snapshots?belowReorderPoint=true}.
 */
@Slf4j
@Component
public class InventoryVisibilityStubAdapter implements InventoryVisibilityPort {

    @Override
    public List<SkuWarehouseQty> findAllBelowReorderPoint(String tenantId) {
        log.info("InventoryVisibilityStubAdapter: IVS REST client not yet wired (BE-025). " +
                 "Batch sweep will find no candidates from IVS in this phase.");
        return List.of();
    }
}
