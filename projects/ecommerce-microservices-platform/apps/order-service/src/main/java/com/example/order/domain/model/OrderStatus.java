package com.example.order.domain.model;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    STUCK_RECOVERY_FAILED;

    public boolean isCancellable() {
        return this == PENDING || this == CONFIRMED;
    }
}
