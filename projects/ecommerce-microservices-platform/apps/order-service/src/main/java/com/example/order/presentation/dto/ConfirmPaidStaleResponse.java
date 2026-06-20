package com.example.order.presentation.dto;

import com.example.order.application.dto.ConfirmPaidStaleResult;

import java.util.List;

/**
 * Response body for {@code POST /api/internal/orders/confirm-paid-stale} (TASK-BE-412).
 *
 * <p>{@code scanned == confirmed + skipped} on a clean run. {@code confirmedOrderIds}
 * is the audit list of ids confirmed this call (always present, possibly empty).
 */
public record ConfirmPaidStaleResponse(
        int scanned,
        int confirmed,
        int skipped,
        List<String> confirmedOrderIds
) {
    public static ConfirmPaidStaleResponse from(ConfirmPaidStaleResult result) {
        return new ConfirmPaidStaleResponse(
                result.scanned(),
                result.confirmed(),
                result.skipped(),
                result.confirmedOrderIds());
    }
}
