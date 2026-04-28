package com.wms.inventory.domain.model;

import com.wms.inventory.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reservation aggregate root — the W4/W5 two-phase allocation against
 * {@link Inventory}.
 *
 * <p>Authoritative reference:
 * {@code specs/services/inventory-service/domain-model.md} §3.
 *
 * <p>State machine:
 * {@code RESERVED → CONFIRMED} (via {@link #confirm(Instant, String)}) or
 * {@code RESERVED → RELEASED} (via {@link #release(ReleasedReason, Instant, String)}).
 * Both terminal states are immutable. Direct status mutation is forbidden.
 *
 * <p>Lines are exposed as an unmodifiable list; the aggregate's caller never
 * adds / removes lines after creation.
 */
public class Reservation {

    private final UUID id;
    private final UUID pickingRequestId;
    private final UUID warehouseId;
    private final List<ReservationLine> lines;
    private ReservationStatus status;
    private final Instant expiresAt;
    private ReleasedReason releasedReason;
    private Instant confirmedAt;
    private Instant releasedAt;
    private long version;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    private Reservation(UUID id, UUID pickingRequestId, UUID warehouseId,
                        List<ReservationLine> lines, ReservationStatus status,
                        Instant expiresAt, ReleasedReason releasedReason,
                        Instant confirmedAt, Instant releasedAt,
                        long version, Instant createdAt, String createdBy,
                        Instant updatedAt, String updatedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.pickingRequestId = Objects.requireNonNull(pickingRequestId, "pickingRequestId");
        this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId");
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Reservation must have at least one line");
        }
        this.lines = List.copyOf(lines);
        this.status = Objects.requireNonNull(status, "status");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.releasedReason = releasedReason;
        this.confirmedAt = confirmedAt;
        this.releasedAt = releasedAt;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy");
    }

    /**
     * Create a new reservation in {@code RESERVED} status. {@code expiresAt}
     * must be strictly after {@code createdAt}.
     */
    public static Reservation create(UUID id, UUID pickingRequestId, UUID warehouseId,
                                     List<ReservationLine> lines, Instant expiresAt,
                                     Instant now, String actorId) {
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException(
                    "expiresAt must be strictly after now; expiresAt=" + expiresAt + ", now=" + now);
        }
        return new Reservation(id, pickingRequestId, warehouseId, lines,
                ReservationStatus.RESERVED, expiresAt,
                null, null, null,
                0L, now, actorId, now, actorId);
    }

    public static Reservation restore(UUID id, UUID pickingRequestId, UUID warehouseId,
                                      List<ReservationLine> lines, ReservationStatus status,
                                      Instant expiresAt, ReleasedReason releasedReason,
                                      Instant confirmedAt, Instant releasedAt,
                                      long version, Instant createdAt, String createdBy,
                                      Instant updatedAt, String updatedBy) {
        return new Reservation(id, pickingRequestId, warehouseId, lines, status,
                expiresAt, releasedReason, confirmedAt, releasedAt,
                version, createdAt, createdBy, updatedAt, updatedBy);
    }

    public void confirm(Instant now, String actorId) {
        if (status != ReservationStatus.RESERVED) {
            throw new StateTransitionInvalidException(
                    "Cannot confirm reservation " + id + " in status " + status);
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = now;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void release(ReleasedReason reason, Instant now, String actorId) {
        Objects.requireNonNull(reason, "reason");
        if (status != ReservationStatus.RESERVED) {
            throw new StateTransitionInvalidException(
                    "Cannot release reservation " + id + " in status " + status);
        }
        this.status = ReservationStatus.RELEASED;
        this.releasedReason = reason;
        this.releasedAt = now;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID id() { return id; }
    public UUID pickingRequestId() { return pickingRequestId; }
    public UUID warehouseId() { return warehouseId; }
    public List<ReservationLine> lines() { return lines; }
    public ReservationStatus status() { return status; }
    public Instant expiresAt() { return expiresAt; }
    public ReleasedReason releasedReason() { return releasedReason; }
    public Instant confirmedAt() { return confirmedAt; }
    public Instant releasedAt() { return releasedAt; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
    public String updatedBy() { return updatedBy; }

    public void incrementVersion() {
        this.version += 1;
    }
}
