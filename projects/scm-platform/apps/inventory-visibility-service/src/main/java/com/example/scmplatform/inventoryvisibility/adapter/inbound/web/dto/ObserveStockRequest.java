package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Request body for {@code POST /api/inventory-visibility/nodes/{nodeId}/observed-stock}
 * (ADR-MONO-054 §D4 / TASK-SCM-BE-047) — a full, absolute reading of the 3PL node's
 * stock at {@code observedAt} (or "now" when omitted).
 */
public record ObserveStockRequest(Instant observedAt, List<Line> lines) {

    /** A single observed SKU quantity — validated non-blank sku / non-negative quantity in the controller. */
    public record Line(String skuCode, BigDecimal quantity) {
    }
}
