package com.wms.outbound.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PickingConfirmation aggregate (pure POJO — no JPA annotations).
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §3.
 *
 * <p>Append-only — once created, never mutated. The aggregate has no setters.
 */
public final class PickingConfirmation {

    private final UUID id;
    private final UUID pickingRequestId;
    private final UUID orderId;
    private final String confirmedBy;
    private final Instant confirmedAt;
    private final String notes;
    private final List<PickingConfirmationLine> lines;

    public PickingConfirmation(UUID id,
                               UUID pickingRequestId,
                               UUID orderId,
                               String confirmedBy,
                               Instant confirmedAt,
                               String notes,
                               List<PickingConfirmationLine> lines) {
        this.id = Objects.requireNonNull(id, "id");
        this.pickingRequestId = Objects.requireNonNull(pickingRequestId, "pickingRequestId");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.confirmedBy = Objects.requireNonNull(confirmedBy, "confirmedBy");
        this.confirmedAt = Objects.requireNonNull(confirmedAt, "confirmedAt");
        this.notes = notes;
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("PickingConfirmation must have at least one line");
        }
        this.lines = new ArrayList<>(lines);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPickingRequestId() {
        return pickingRequestId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public String getNotes() {
        return notes;
    }

    public List<PickingConfirmationLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
