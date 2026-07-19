package com.example.scmplatform.demandplanning.application.port.outbound;

import java.util.UUID;

/**
 * Outbound port for the synchronous intra-scm procurement leg (ADR-MONO-027 D5):
 * hand an approved reorder suggestion to procurement, which creates a DRAFT
 * purchase order. Idempotent on {@code sourceSuggestionId} at the procurement
 * side — a repeated call returns the same PO.
 *
 * <p>v2 may replace this synchronous REST adapter with an intra-scm event
 * (D5) by swapping the implementation — the port is the seam.
 */
public interface ProcurementDraftPoPort {

    /**
     * Create (or idempotently fetch) the DRAFT PO for the given suggestion.
     *
     * @param command     the resolved supplier + line to draft
     * @param bearerToken the operator's {@code Authorization} header value
     *                    (full {@code "Bearer <jwt>"}), propagated for intra-scm
     *                    trust; may be {@code null} in non-authenticated contexts.
     * @return the created/existing PO reference
     * @throws com.example.scmplatform.demandplanning.domain.error.ProcurementUnavailableException
     *         if procurement is unreachable or returns an error
     */
    DraftPoResult createDraftFromSuggestion(DraftPoCommand command, String bearerToken);

    record DraftPoCommand(UUID sourceSuggestionId, UUID supplierId, String currency,
                          String skuCode, int quantity,
                          // ADR-MONO-050 D1/D3: the warehouse that seeded the reorder
                          // (→ procurement destinationWarehouseId) + sku_supplier_map
                          // lead time (→ expectedArrivalDate). Enables the wms
                          // inbound-expected event on PO CONFIRMED.
                          UUID destinationWarehouseId, int leadTimeDays) {
    }

    record DraftPoResult(String poId, String poStatus) {
    }
}
