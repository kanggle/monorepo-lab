package com.example.scmplatform.procurement.domain.po.repository;

import java.time.Instant;

/**
 * One {@code po_status_history} transition <em>to</em> {@code RECEIVED} inside a metrics
 * window (TASK-MONO-810) — the PO it belongs to and when it happened. The application
 * service counts distinct {@code poId}s ({@code receivedCount}).
 */
public record ReceivedTransitionRow(String poId, Instant occurredAt) {
}
