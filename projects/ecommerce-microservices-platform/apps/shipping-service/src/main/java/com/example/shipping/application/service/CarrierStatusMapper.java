package com.example.shipping.application.service;

import com.example.shipping.domain.model.ShippingStatus;

import java.util.Optional;

/**
 * Maps a carrier-reported raw status string to the domain {@link ShippingStatus}
 * (TASK-BE-293). Tolerant of common carrier vocabularies / separators; an unknown or
 * blank status maps to {@link Optional#empty()} (the refresh treats it as "no usable
 * signal" → no-op). {@code PREPARING} is intentionally NOT a carrier target — a
 * shipment only reaches a carrier once it is SHIPPED with a tracking number.
 */
public final class CarrierStatusMapper {

    private CarrierStatusMapper() {
    }

    public static Optional<ShippingStatus> toShippingStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawStatus.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "SHIPPED", "DISPATCHED", "PICKED_UP" -> Optional.of(ShippingStatus.SHIPPED);
            case "IN_TRANSIT", "INTRANSIT", "OUT_FOR_DELIVERY" -> Optional.of(ShippingStatus.IN_TRANSIT);
            case "DELIVERED", "COMPLETED" -> Optional.of(ShippingStatus.DELIVERED);
            default -> Optional.empty();
        };
    }
}
