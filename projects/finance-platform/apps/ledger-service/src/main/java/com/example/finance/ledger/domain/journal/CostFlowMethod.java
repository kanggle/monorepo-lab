package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.error.LedgerErrors.CostFlowMethodInvalidException;

/**
 * FX cost-flow method enum for the per-tenant config (15th increment — TASK-FIN-BE-023,
 * ADR-001 D1). Controls the carrying-basis algorithm applied when settling a portion of a
 * foreign position. Only {@code WEIGHTED_AVERAGE} and {@code FIFO} are supported;
 * {@code LIFO} is excluded by ADR-001 D1 (IFRS prohibition).
 *
 * <p>Settlement still uses weighted-average in this increment (shadow — FIN-BE-025 wires
 * FIFO consumption when the config is set to {@code FIFO}).
 */
public enum CostFlowMethod {

    /** Default: proportional weighted-average carrying basis (existing behaviour). */
    WEIGHTED_AVERAGE,

    /** FIFO: earliest lots are consumed first (wired by FIN-BE-025). */
    FIFO;

    /**
     * Resolve a {@code CostFlowMethod} from a string value (exact-match uppercase only —
     * no normalisation; unknown or {@code null} → {@link CostFlowMethodInvalidException}).
     *
     * @param value the raw string from the request or persistence (e.g. {@code "FIFO"})
     * @return the matching enum constant
     * @throws CostFlowMethodInvalidException if {@code value} is {@code null}, blank, or
     *         does not exactly match {@code WEIGHTED_AVERAGE} or {@code FIFO}
     */
    public static CostFlowMethod fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new CostFlowMethodInvalidException(
                    "method must be one of WEIGHTED_AVERAGE, FIFO — got: null/blank");
        }
        return switch (value) {
            case "WEIGHTED_AVERAGE" -> WEIGHTED_AVERAGE;
            case "FIFO"             -> FIFO;
            default -> throw new CostFlowMethodInvalidException(
                    "method must be one of WEIGHTED_AVERAGE, FIFO — got: " + value);
        };
    }
}
