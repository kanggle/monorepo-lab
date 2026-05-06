package com.example.scmplatform.procurement.domain.po.status;

/**
 * PO state machine vocabulary, mirroring
 * {@code rules/domains/scm.md} § Ubiquitous Language.
 *
 * <p>Linear progression: DRAFT → SUBMITTED → ACKNOWLEDGED → CONFIRMED →
 * (PARTIALLY_RECEIVED →) RECEIVED → SETTLED → CLOSED. {@code CANCELED} is a
 * branch reachable from DRAFT / SUBMITTED / ACKNOWLEDGED only — once
 * CONFIRMED, cancellation requires a corrective task (out of v1 scope).
 */
public enum PoStatus {
    DRAFT,
    SUBMITTED,
    ACKNOWLEDGED,
    CONFIRMED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    SETTLED,
    CLOSED,
    CANCELED
}
