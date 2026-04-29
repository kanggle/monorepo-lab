package com.wms.inbound.adapter.out.persistence.putaway;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only JPA entity for {@code putaway_confirmation}. The adapter never
 * issues UPDATE / DELETE — domain-model.md §4 invariant; W7 trigger enforces
 * at the DB level.
 */
@Entity
@Table(name = "putaway_confirmation")
class PutawayConfirmationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "putaway_instruction_id", nullable = false)
    private UUID putawayInstructionId;

    @Column(name = "putaway_line_id", nullable = false, unique = true)
    private UUID putawayLineId;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "planned_location_id", nullable = false)
    private UUID plannedLocationId;

    @Column(name = "actual_location_id", nullable = false)
    private UUID actualLocationId;

    @Column(name = "qty_confirmed", nullable = false)
    private int qtyConfirmed;

    @Column(name = "confirmed_by", nullable = false, length = 100)
    private String confirmedBy;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    protected PutawayConfirmationJpaEntity() {}

    PutawayConfirmationJpaEntity(UUID id, UUID putawayInstructionId, UUID putawayLineId,
                                  UUID skuId, UUID lotId,
                                  UUID plannedLocationId, UUID actualLocationId,
                                  int qtyConfirmed, String confirmedBy, Instant confirmedAt) {
        this.id = id;
        this.putawayInstructionId = putawayInstructionId;
        this.putawayLineId = putawayLineId;
        this.skuId = skuId;
        this.lotId = lotId;
        this.plannedLocationId = plannedLocationId;
        this.actualLocationId = actualLocationId;
        this.qtyConfirmed = qtyConfirmed;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt;
    }

    UUID getId() { return id; }
    UUID getPutawayInstructionId() { return putawayInstructionId; }
    UUID getPutawayLineId() { return putawayLineId; }
    UUID getSkuId() { return skuId; }
    UUID getLotId() { return lotId; }
    UUID getPlannedLocationId() { return plannedLocationId; }
    UUID getActualLocationId() { return actualLocationId; }
    int getQtyConfirmed() { return qtyConfirmed; }
    String getConfirmedBy() { return confirmedBy; }
    Instant getConfirmedAt() { return confirmedAt; }
}
