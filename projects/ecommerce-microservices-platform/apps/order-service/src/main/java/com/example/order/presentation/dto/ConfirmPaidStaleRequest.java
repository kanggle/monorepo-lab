package com.example.order.presentation.dto;

/**
 * Request body for {@code POST /api/internal/orders/confirm-paid-stale} (TASK-BE-412).
 *
 * <p>Both fields are optional in the body — a missing field falls back to its default
 * ({@code olderThanMinutes} 30, {@code limit} 200) via {@link #resolvedOlderThanMinutes()}
 * / {@link #resolvedLimit()}. Range constraints ({@code olderThanMinutes >= 1},
 * {@code limit} in {@code 1..1000}) are enforced in the controller so an out-of-range
 * value surfaces as {@code 400 INVALID_REQUEST} per the contract (rather than the generic
 * bean-validation envelope). Defaults are applied before the range check, so an explicit
 * {@code null} is treated as "use the default", never as a violation.
 */
public record ConfirmPaidStaleRequest(
        Integer olderThanMinutes,
        Integer limit
) {

    public static final int DEFAULT_OLDER_THAN_MINUTES = 30;
    public static final int DEFAULT_LIMIT = 200;
    public static final int MIN_OLDER_THAN_MINUTES = 1;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 1000;

    public int resolvedOlderThanMinutes() {
        return olderThanMinutes != null ? olderThanMinutes : DEFAULT_OLDER_THAN_MINUTES;
    }

    public int resolvedLimit() {
        return limit != null ? limit : DEFAULT_LIMIT;
    }
}
