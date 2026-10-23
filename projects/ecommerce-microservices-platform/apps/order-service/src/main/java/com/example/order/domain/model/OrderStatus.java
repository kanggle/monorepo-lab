package com.example.order.domain.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    BACKORDERED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    STUCK_RECOVERY_FAILED;

    /**
     * The statuses whose orders count as sales — revenue, units and order count on the
     * operator overview, and the insights rankings (ADR-MONO-084 D5, TASK-MONO-807): paid
     * <em>and</em> stock-reserved. {@code CONFIRMED} is reached only through the payment /
     * stock-reservation saga (TASK-BE-648), and {@code SHIPPED} / {@code DELIVERED} only
     * after it. Not {@code PENDING} (unpaid), not {@code BACKORDERED} (paid, stock not held —
     * it joins on its own {@code paidAt} date once it is confirmed, ADR-084 OD-1), not
     * {@code CANCELLED} (refunds are only ever made on a cancelled order), not
     * {@code STUCK_RECOVERY_FAILED}.
     */
    public static final Set<OrderStatus> REALISED_SALE =
            Collections.unmodifiableSet(EnumSet.of(CONFIRMED, SHIPPED, DELIVERED));

    public boolean isCancellable() {
        return this == PENDING || this == CONFIRMED || this == BACKORDERED;
    }
}
