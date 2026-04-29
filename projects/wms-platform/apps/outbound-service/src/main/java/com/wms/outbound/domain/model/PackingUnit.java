package com.wms.outbound.domain.model;

import com.wms.outbound.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PackingUnit aggregate (pure POJO — no JPA annotations).
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §4.
 *
 * <p>Lifecycle: {@link PackingUnitStatus#OPEN} → {@link PackingUnitStatus#SEALED}
 * (irreversible). Once {@code SEALED} no lines can be added or removed; the
 * domain enforces this in the persistence adapter via the {@code seal()} method
 * and via the application service that rejects mutations on a sealed unit.
 */
public final class PackingUnit {

    private final UUID id;
    private final UUID orderId;
    private final String cartonNo;
    private final PackingType packingType;
    private final Integer weightGrams;
    private final Integer lengthMm;
    private final Integer widthMm;
    private final Integer heightMm;
    private final String notes;
    private PackingUnitStatus status;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<PackingUnitLine> lines;

    public PackingUnit(UUID id,
                       UUID orderId,
                       String cartonNo,
                       PackingType packingType,
                       Integer weightGrams,
                       Integer lengthMm,
                       Integer widthMm,
                       Integer heightMm,
                       String notes,
                       PackingUnitStatus status,
                       long version,
                       Instant createdAt,
                       Instant updatedAt,
                       List<PackingUnitLine> lines) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.cartonNo = Objects.requireNonNull(cartonNo, "cartonNo");
        this.packingType = Objects.requireNonNull(packingType, "packingType");
        this.weightGrams = weightGrams;
        this.lengthMm = lengthMm;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.notes = notes;
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("PackingUnit must have at least one line");
        }
        this.lines = new ArrayList<>(lines);
    }

    /**
     * Domain transition: {@link PackingUnitStatus#OPEN} →
     * {@link PackingUnitStatus#SEALED}.
     *
     * <p>Idempotent: re-sealing an already-{@code SEALED} unit is rejected
     * via {@link StateTransitionInvalidException} so the caller can tell
     * "I sealed it" from "someone else already sealed it" — the
     * Idempotency-Key cache absorbs the genuine "double click" case.
     */
    public void seal(Instant now) {
        if (status == PackingUnitStatus.SEALED) {
            throw new StateTransitionInvalidException(
                    PackingUnitStatus.SEALED.name(), PackingUnitStatus.SEALED.name());
        }
        this.status = PackingUnitStatus.SEALED;
        this.updatedAt = now;
    }

    public boolean isSealed() {
        return status == PackingUnitStatus.SEALED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getCartonNo() {
        return cartonNo;
    }

    public PackingType getPackingType() {
        return packingType;
    }

    public Integer getWeightGrams() {
        return weightGrams;
    }

    public Integer getLengthMm() {
        return lengthMm;
    }

    public Integer getWidthMm() {
        return widthMm;
    }

    public Integer getHeightMm() {
        return heightMm;
    }

    public String getNotes() {
        return notes;
    }

    public PackingUnitStatus getStatus() {
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

    public List<PackingUnitLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
