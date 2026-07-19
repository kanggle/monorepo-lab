package com.example.scmplatform.procurement.domain.po.status;

/**
 * PO state machine vocabulary, mirroring
 * {@code rules/domains/scm.md} § Ubiquitous Language.
 *
 * <p>Linear progression: DRAFT → SUBMITTED → ACKNOWLEDGED → CONFIRMED →
 * (PARTIALLY_RECEIVED →) RECEIVED → SETTLED → CLOSED. {@code CANCELED} is a
 * branch reachable from DRAFT / SUBMITTED / ACKNOWLEDGED / CONFIRMED — the
 * CONFIRMED cancel path (ADR-MONO-050 D6.3 / SCM-BE-036) is allowed only while
 * not-yet-received; once any goods arrive (PARTIALLY_RECEIVED / RECEIVED)
 * cancellation is forbidden and belongs to the return domain (out of v1 scope).
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
