package com.wms.inventory.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Line of a {@link Reservation}. Each line ties to one Inventory row and
 * contributes to its {@code reserved_qty}.
 *
 * <p>Lines have no version of their own — the parent Reservation aggregate's
 * version covers the whole graph. Mutations on a line after the Reservation
 * reaches a terminal state ({@code CONFIRMED} or {@code RELEASED}) are
 * forbidden.
 */
public final class ReservationLine {

    private final UUID id;
    private final UUID reservationId;
    private final UUID inventoryId;
    private final UUID locationId;
    private final UUID skuId;
    private final UUID lotId;
    private final int quantity;

    public ReservationLine(UUID id, UUID reservationId, UUID inventoryId,
                           UUID locationId, UUID skuId, UUID lotId, int quantity) {
        this.id = Objects.requireNonNull(id, "id");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        this.inventoryId = Objects.requireNonNull(inventoryId, "inventoryId");
        this.locationId = Objects.requireNonNull(locationId, "locationId");
        this.skuId = Objects.requireNonNull(skuId, "skuId");
        this.lotId = lotId;
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0, got: " + quantity);
        }
        this.quantity = quantity;
    }

    public UUID id() { return id; }
    public UUID reservationId() { return reservationId; }
    public UUID inventoryId() { return inventoryId; }
    public UUID locationId() { return locationId; }
    public UUID skuId() { return skuId; }
    public UUID lotId() { return lotId; }
    public int quantity() { return quantity; }
}
