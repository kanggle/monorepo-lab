package com.example.finance.ledger.application;

import java.math.BigDecimal;

/**
 * Command to upsert a tenant's FX contract-rate override (28th increment — TASK-FIN-BE-042,
 * ADR-002 § 3.1 per-tenant override / 특수 계약환율). The controller maps the validated request
 * body + the path foreign-currency + the resolved {@link ActorContext} identity to this.
 * {@code actor} is the audit {@code updated_by} (the JWT subject, else the tenant — same identity
 * rule as the cost-flow / journal mutations). {@code base} is the fixed reporting currency code
 * (KRW in v1); {@code foreign} is the override's foreign leg. A non-positive / invalid {@code rate}
 * or an unknown currency → {@code VALIDATION_ERROR} (400).
 *
 * <p>Money: {@code rate} is an EXACT {@link BigDecimal} (base-minor-per-foreign-minor) — no
 * {@code float}/{@code double} (regulated F5).
 *
 * @param tenantId the tenant whose contract rate is upserted (row-level isolation, AC-3)
 * @param base     the base/reporting currency code (KRW in v1)
 * @param foreign  the foreign currency code of the pair
 * @param rate     the exact contract rate (strictly positive)
 * @param actor    the audit actor recorded as {@code updated_by}
 */
public record SetFxRateOverrideCommand(String tenantId, String base, String foreign,
                                       BigDecimal rate, String actor) {
}
