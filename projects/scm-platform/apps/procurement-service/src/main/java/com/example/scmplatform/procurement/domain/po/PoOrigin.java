package com.example.scmplatform.procurement.domain.po;

/**
 * Provenance of a {@link PurchaseOrder} (ADR-MONO-027 D5).
 *
 * <ul>
 *   <li>{@code OPERATOR} — the default: a buyer/operator authored the PO directly
 *       via {@code POST /api/procurement/po}.</li>
 *   <li>{@code DEMAND_PLANNING} — the PO was materialized from an approved reorder
 *       suggestion via the additive {@code POST /api/procurement/po/from-suggestion}
 *       entry. Carries a {@code sourceSuggestionId} for cross-service idempotency.</li>
 * </ul>
 *
 * Origin is descriptive only — it never affects the PO state machine; both
 * origins share the identical {@code DRAFT → SUBMITTED → …} lifecycle.
 */
public enum PoOrigin {
    OPERATOR,
    DEMAND_PLANNING
}
