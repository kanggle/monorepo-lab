package com.example.finance.ledger.presentation.dto;

/**
 * Upsert body for {@code PUT /settlements/cost-flow-config} (15th increment —
 * TASK-FIN-BE-023). {@code method} must be {@code "WEIGHTED_AVERAGE"} or {@code "FIFO"}
 * (exact-match uppercase; the use case enforces → {@code VALIDATION_ERROR} 400 on unknown
 * values; the DB CHECK is the structural backstop). {@code null} / blank → invalid.
 */
public record FxCostFlowConfigRequest(String method) {
}
