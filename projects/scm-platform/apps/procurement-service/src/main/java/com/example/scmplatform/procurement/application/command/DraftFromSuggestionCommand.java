package com.example.scmplatform.procurement.application.command;

import com.example.scmplatform.procurement.application.ActorContext;

import java.util.List;

/**
 * Command for {@code POST /api/procurement/po/from-suggestion} — create a DRAFT PO
 * materialized from an approved reorder suggestion (ADR-MONO-027 D5).
 *
 * <p>Idempotent on {@link #sourceSuggestionId}: a repeated call for the same
 * suggestion returns the existing PO, never a duplicate. No auto-SUBMIT — the
 * PO lands in DRAFT for operator review.
 *
 * <p>{@code unitPriceRef} carries a price <em>reference/placeholder</em> (e.g.
 * {@code "LAST_KNOWN"}), not a computed price — demand-planning invents no
 * pricing. v1 persists a zero unit price pending operator review.
 */
public record DraftFromSuggestionCommand(
        ActorContext actor,
        String supplierId,
        String currency,
        String sourceSuggestionId,
        List<Line> lines
) {
    public record Line(int lineNo, String sku, int quantity, String unitPriceRef) {
    }
}
