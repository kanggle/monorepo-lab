package com.example.order.application.dto;

import java.util.List;

/**
 * Result of a stale-paid forward-confirm sweep (TASK-BE-412).
 *
 * <p>{@code scanned == confirmed + skipped} on a clean run. Per-order failures
 * (optimistic-lock conflict, etc.) are isolated, excluded from {@code confirmed},
 * left for the next tick — so on a partial run {@code confirmed + skipped} may be
 * less than {@code scanned}.
 *
 * @param scanned           orders matched by the predicate this call
 * @param confirmed         orders transitioned {@code PENDING → CONFIRMED} (event emitted)
 * @param skipped           orders no-op'd (already CONFIRMED, or raced out of PENDING)
 * @param confirmedOrderIds ids confirmed this call (audit / caller logging)
 */
public record ConfirmPaidStaleResult(
        int scanned,
        int confirmed,
        int skipped,
        List<String> confirmedOrderIds
) {
}
