package com.example.scmplatform.inventoryvisibility.domain.expectation;

/**
 * Lifecycle of a 3PL inbound expectation (ADR-MONO-055 §D4 / TASK-SCM-BE-049).
 *
 * <p>Deliberately two-valued — this is a read-model projection, not a domain
 * state machine (contrast the procurement PO lifecycle). An expectation is
 * recorded {@code OPEN} when a {@code THIRD_PARTY_LOGISTICS}-addressed PO is
 * confirmed, and flips to {@code SATISFIED} once a 3PL observation
 * (TASK-SCM-BE-047) shows the expected stock has landed. An unmet expectation
 * stays {@code OPEN} — a visible, aging operational signal, never silently
 * purged.
 */
public enum ExpectationStatus {
    OPEN,
    SATISFIED
}
