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

    record DraftPoCommand(UUID sourceSuggestionId, String supplierId, String currency,
                          String skuCode, int quantity,
                          // ADR-MONO-050 D1/D3/D9: the warehouse CODE that seeded the
                          // reorder (→ procurement destinationWarehouseId, resolved by wms
                          // via findWarehouseByCode) + sku_supplier_map lead time
                          // (→ expectedArrivalDate). Enables the wms inbound-expected event
                          // on PO CONFIRMED. destinationWarehouseId is null for BATCH
                          // suggestions with no code source → no inbound-expected emitted.
                          // supplierId is likewise a supplier CODE (wms findPartnerByCode).
                          String destinationWarehouseId,
                          // ADR-MONO-055 §D2/§D3: the destination node TYPE from the
                          // suggestion (→ procurement destinationNodeType). WMS_WAREHOUSE for
                          // the alert path + wms batch nodes; THIRD_PARTY_LOGISTICS for a 3PL
                          // node (which carries no warehouse code, so no inbound-expected is
                          // emitted toward wms — correct interim, BE-049 routes it to the sink).
                          String destinationNodeType, int leadTimeDays) {
    }

    record DraftPoResult(String poId, String poStatus) {
    }
}
