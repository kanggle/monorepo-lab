package com.example.scmplatform.demandplanning.domain.model;

/**
 * Status machine for ReorderSuggestion (ADR-027 data-model.md).
 *
 * <pre>
 * SUGGESTED ──approve──> APPROVED ──materialize──> MATERIALIZED
 *     │                     │
 *     └──dismiss──┐         └──dismiss──┐
 *                 ▼                     ▼
 *              DISMISSED            DISMISSED
 * </pre>
 *
 * MATERIALIZED and DISMISSED are terminal.
 */
public enum SuggestionStatus {
    SUGGESTED,
    APPROVED,
    MATERIALIZED,
    DISMISSED;

    public boolean isTerminal() {
        return this == MATERIALIZED || this == DISMISSED;
    }

    public boolean canDismiss() {
        return this == SUGGESTED || this == APPROVED;
    }

    public boolean canApprove() {
        return this == SUGGESTED;
    }
}
