package com.example.finance.ledger.presentation.dto;

/**
 * Upsert body for {@code PUT /settlements/cost-flow-config/accounts/{ledgerAccountCode}} (21st
 * increment — TASK-FIN-BE-029). {@code method} must be {@code "WEIGHTED_AVERAGE"} or
 * {@code "FIFO"} (exact-match uppercase; the use case enforces → {@code VALIDATION_ERROR} 400 on
 * unknown values; the DB CHECK is the structural backstop). {@code null} / blank → invalid. The
 * account code is the path variable (not in the body).
 */
public record FxCostFlowAccountConfigRequest(String method) {
}
