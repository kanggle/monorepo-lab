package com.example.shipping.domain.model;

import java.util.Map;
import java.util.Set;

/**
 * Shipping lifecycle: {@code PREPARING → SHIPPED → IN_TRANSIT → DELIVERED}, plus the terminal
 * side branch {@code PREPARING → CANCELLED} (ADR-MONO-022 D4 v2(d), TASK-BE-649).
 *
 * <p><b>Declaration order is load-bearing.</b> {@code ShippingForwardAdvancer} walks
 * {@link #values()} by ordinal toward a carrier-reported goal. The forward chain must stay in
 * order, and {@code CANCELLED} must stay <b>last</b>: no carrier goal (always one of
 * {@code SHIPPED}/{@code IN_TRANSIT}/{@code DELIVERED}) is then ever "ahead" of a cancelled
 * shipment, so a refresh, webhook or auto-collect can never resurrect it.
 */
public enum ShippingStatus {

    PREPARING,
    SHIPPED,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED;

    private static final Map<ShippingStatus, Set<ShippingStatus>> ALLOWED_TRANSITIONS = Map.of(
            PREPARING, Set.of(SHIPPED, CANCELLED),
            SHIPPED, Set.of(IN_TRANSIT),
            IN_TRANSIT, Set.of(DELIVERED)
    );

    public boolean canTransitionTo(ShippingStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public static Set<ShippingStatus> allStatuses() {
        return Set.of(values());
    }
}
