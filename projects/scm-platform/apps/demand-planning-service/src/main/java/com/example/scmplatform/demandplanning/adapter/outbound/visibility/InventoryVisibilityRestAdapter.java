package com.example.scmplatform.demandplanning.adapter.outbound.visibility;

import com.example.common.resilience.ResilienceClientFactory;
import com.example.scmplatform.demandplanning.application.port.outbound.InventoryVisibilityPort;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Live IVS read adapter for the nightly replenishment batch (ADR-MONO-027 §D7.1).
 *
 * <p>Calls the IVS <b>internal</b> network-trusted endpoint
 * {@code GET /internal/inventory-visibility/snapshot} — <b>no bearer token</b>:
 * the sweep is unattended ({@code @Scheduled}, no operator token) and scm has no
 * workload-identity infra. The endpoint is reachable only on the intra-scm
 * container network (scm-gateway routes only {@code /api/v1/**}). Mirrors the
 * {@code ProcurementDraftPoClient} RestClient construction (D5).
 *
 * <p>Returns every snapshot row across tenants;
 * {@link com.example.scmplatform.demandplanning.application.usecase.SweepReorderUseCase}
 * filters each against the reorder policy. On any transport / non-2xx / parse
 * failure this method throws, which the use case catches → it skips the run and
 * increments {@code reorder_sweep_ivs_unavailable_total} (S5 decoupling — the
 * live alert path is unaffected).
 */
@Slf4j
@Component
public class InventoryVisibilityRestAdapter implements InventoryVisibilityPort {

    private static final String SNAPSHOT_PATH = "/internal/inventory-visibility/snapshot";

    private final RestClient client;

    public InventoryVisibilityRestAdapter(
            @Value("${scmplatform.demand-planning.inventory-visibility.base-url}") String baseUrl,
            @Value("${scmplatform.demand-planning.inventory-visibility.connect-timeout-ms:2000}") int connectMs,
            @Value("${scmplatform.demand-planning.inventory-visibility.read-timeout-ms:10000}") int readMs) {
        this.client = ResilienceClientFactory.buildRestClient(baseUrl, connectMs, readMs);
    }

    @Override
    public List<SkuWarehouseQty> findAllBelowReorderPoint(String tenantId) {
        JsonNode response = client.get()
                .uri(SNAPSHOT_PATH)
                .retrieve()
                .body(JsonNode.class);

        List<SkuWarehouseQty> result = new ArrayList<>();
        if (response == null || !response.hasNonNull("data")) {
            log.warn("IVS internal snapshot returned no data node");
            return result;
        }
        for (JsonNode row : response.get("data")) {
            try {
                String sku = row.path("sku").asText(null);
                String nodeId = row.path("nodeId").asText(null);
                if (sku == null || nodeId == null) {
                    continue;
                }
                int availableQty = row.path("availableQty").asInt();
                // ADR-MONO-050 D9: additive + nullable — absent on older IVS builds and null
                // until IVS learns the code from a wms mutation event. Never a parse failure.
                String warehouseCode = row.path("warehouseCode").asText(null);
                result.add(new SkuWarehouseQty(sku, UUID.fromString(nodeId), availableQty,
                        warehouseCode));
            } catch (RuntimeException e) {
                // Skip a single malformed row; never abort the whole sweep candidate set.
                log.warn("IVS internal snapshot: skipping malformed row {}: {}", row, e.getMessage());
            }
        }
        log.info("IVS internal snapshot: {} candidate rows read", result.size());
        return result;
    }
}
