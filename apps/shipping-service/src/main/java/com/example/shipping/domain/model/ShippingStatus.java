package com.example.shipping.domain.model;

import java.util.Map;
import java.util.Set;

public enum ShippingStatus {

    PREPARING,
    SHIPPED,
    IN_TRANSIT,
    DELIVERED;

    private static final Map<ShippingStatus, ShippingStatus> ALLOWED_TRANSITIONS = Map.of(
            PREPARING, SHIPPED,
            SHIPPED, IN_TRANSIT,
            IN_TRANSIT, DELIVERED
    );

    public boolean canTransitionTo(ShippingStatus target) {
        return ALLOWED_TRANSITIONS.get(this) == target;
    }

    public static Set<ShippingStatus> allStatuses() {
        return Set.of(values());
    }
}
