package com.example.finance.ledger.application;

/**
 * Command to upsert a tenant's FX reconciliation tolerance (13th increment —
 * TASK-FIN-BE-020). The controller maps the validated request body + the resolved
 * {@link ActorContext} identity to this. {@code actor} is the audit {@code updated_by}
 * (the JWT subject, else the tenant — same identity rule as the journal/period
 * mutations). Negative {@code toleranceBps}/{@code floorMinor} → {@code VALIDATION_ERROR}.
 *
 * @param tenantId     the tenant whose tolerance is upserted (row-level isolation)
 * @param toleranceBps the basis-points band term ({@code >= 0})
 * @param floorMinor   the absolute floor in base/KRW minor units ({@code >= 0})
 * @param actor        the audit actor recorded as {@code updated_by}
 */
public record SetFxToleranceCommand(String tenantId, int toleranceBps, long floorMinor,
                                    String actor) {
}
