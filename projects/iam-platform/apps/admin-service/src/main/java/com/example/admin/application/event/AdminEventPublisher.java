package com.example.admin.application.event;

import com.example.admin.application.Outcome;

import java.time.Instant;

/**
 * Port for {@code admin.action.performed} event publishing (TASK-BE-452 — outbox
 * v1 → v2).
 *
 * <p>Previously a concrete {@code BaseEventPublisher} subclass that wrote to the
 * shared lib {@code outbox} via {@code OutboxWriter.saveEvent} (FLAT wire — the
 * canonical-action payload serialised as-is, NO 7-field envelope wrapper). It is now
 * a port; the impl
 * {@link com.example.admin.infrastructure.outbox.OutboxAdminEventPublisher}
 * reproduces the EXACT v1 flat payload (incl. the centralised PII {@code displayHint}
 * masking) and persists an {@code admin_outbox} row driven by the v2 relay.
 *
 * <p>The {@link Envelope} input record is preserved verbatim — it is referenced by
 * {@code AdminActionAuditWriter} / {@code AdminActionDenyWriter} call sites.
 */
public interface AdminEventPublisher {

    void publishAdminActionPerformed(Envelope env);

    /**
     * Canonical input record for {@link #publishAdminActionPerformed(Envelope)}.
     * Keeps the publisher decoupled from {@code AdminActionAuditor}'s records.
     */
    record Envelope(
            String operatorId,
            String sessionId,
            String permission,
            String endpoint,
            String method,
            String targetType,
            String targetId,
            Outcome outcome,
            String reason,
            Instant occurredAt
    ) {}
}
