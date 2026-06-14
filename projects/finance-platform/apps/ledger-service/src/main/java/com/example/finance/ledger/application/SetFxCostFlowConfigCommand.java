package com.example.finance.ledger.application;

/**
 * Command to upsert a tenant's FX cost-flow method config (15th increment —
 * TASK-FIN-BE-023). The controller maps the validated request body + the resolved
 * {@link ActorContext} identity to this. {@code actor} is the audit {@code updated_by}
 * (the JWT subject, else the tenant — same identity rule as the journal/period
 * mutations). An unknown {@code method} string → {@code VALIDATION_ERROR} (400).
 *
 * @param tenantId the tenant whose cost-flow method is upserted (row-level isolation)
 * @param method   the raw method string (e.g. {@code "FIFO"}, {@code "WEIGHTED_AVERAGE"})
 * @param actor    the audit actor recorded as {@code updated_by}
 */
public record SetFxCostFlowConfigCommand(String tenantId, String method, String actor) {
}
