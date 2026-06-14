package com.example.finance.ledger.application;

/**
 * Command to upsert a per-account FX cost-flow method override (21st increment —
 * TASK-FIN-BE-029). The controller maps the validated request body + the path variable +
 * the resolved {@link ActorContext} identity to this. {@code actor} is the audit
 * {@code updated_by} (the JWT subject, else the tenant — same identity rule as the journal/period
 * and per-tenant cost-flow mutations). An unknown {@code method} string →
 * {@code VALIDATION_ERROR} (400).
 *
 * @param tenantId          the tenant whose account override is upserted (row-level isolation)
 * @param ledgerAccountCode the ledger account the override applies to (from the path)
 * @param method            the raw method string (e.g. {@code "FIFO"}, {@code "WEIGHTED_AVERAGE"})
 * @param actor             the audit actor recorded as {@code updated_by}
 */
public record SetFxCostFlowAccountConfigCommand(String tenantId, String ledgerAccountCode,
                                                String method, String actor) {
}
