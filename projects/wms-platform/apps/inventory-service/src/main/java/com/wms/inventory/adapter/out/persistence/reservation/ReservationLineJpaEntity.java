package com.wms.inventory.adapter.out.persistence.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "reservation_line")
public class ReservationLineJpaEntity {

    @Id
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "inventory_id", nullable = false)
    private UUID inventoryId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected ReservationLineJpaEntity() {
    }

    public ReservationLineJpaEntity(UUID id, UUID reservationId, UUID inventoryId,
                                    UUID locationId, UUID skuId, UUID lotId, int quantity) {
        this.id = id;
        this.reservationId = reservationId;
        this.inventoryId = inventoryId;
        this.locationId = locationId;
        this.skuId = skuId;
        this.lotId = lotId;
        this.quantity = quantity;
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public UUID getInventoryId() { return inventoryId; }
    public UUID getLocationId() { return locationId; }
    public UUID getSkuId() { return skuId; }
    public UUID getLotId() { return lotId; }
    public int getQuantity() { return quantity; }
}
