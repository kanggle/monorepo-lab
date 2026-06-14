package com.example.finance.ledger.presentation.dto;

/**
 * Response for {@code DELETE /settlements/cost-flow-config/accounts/{ledgerAccountCode}} (21st
 * increment — TASK-FIN-BE-029). {@code cleared} reports whether an override row actually existed
 * and was removed; a DELETE on a non-existent override returns {@code cleared=false} (idempotent
 * no-op, 200 — not 404). The account then falls back to the per-tenant default.
 *
 * @param ledgerAccountCode the account whose override was targeted
 * @param cleared           {@code true} when a row existed and was removed; {@code false} on a no-op
 */
public record FxCostFlowAccountConfigDeleteResponse(String ledgerAccountCode, boolean cleared) {
}
