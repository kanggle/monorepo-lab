package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto;

import java.time.Instant;

/**
 * Response body for {@code POST /api/inventory-visibility/nodes/{nodeId}/observed-stock}
 * (ADR-MONO-054 §D4 / TASK-SCM-BE-047) — summary of the recorded observation, not the
 * full snapshot (callers re-read via the existing GET endpoints for the current state).
 */
public record ObserveStockResponse(String nodeId, int skuCount, Instant observedAt) {
}
