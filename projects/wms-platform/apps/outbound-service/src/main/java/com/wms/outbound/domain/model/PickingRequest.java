package com.wms.outbound.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PickingRequest aggregate (pure POJO — no JPA annotations).
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §2.
 *
 * <p>One PickingRequest per Order. The {@code id} field doubles as the
 * cross-service {@code reservationId} carried in the
 * {@code outbound.picking.requested} event and echoed back by inventory.
 */
public final class PickingRequest {

    private final UUID id;
    private final UUID orderId;
    private final UUID sagaId;
    private final UUID warehouseId;
    private PickingRequestStatus status;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<PickingRequestLine> lines;

    public PickingRequest(UUID id,
                          UUID orderId,
                          UUID sagaId,
                          UUID warehouseId,
                          PickingRequestStatus status,
                          long version,
                          Instant createdAt,
                          Instant updatedAt,
                          List<PickingRequestLine> lines) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.sagaId = Objects.requireNonNull(sagaId, "sagaId");
        this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId");
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("PickingRequest must have at least one line");
        }
        this.lines = new ArrayList<>(lines);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getSagaId() {
        return sagaId;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public PickingRequestStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<PickingRequestLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
