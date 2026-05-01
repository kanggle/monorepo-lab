package com.wms.inbound.domain.model;

import com.wms.inbound.domain.exception.PutawayLineNotFoundException;
import com.wms.inbound.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for the Putaway phase. One per ASN.
 *
 * <p>Domain methods:
 * <ul>
 *   <li>{@link #confirmLine(UUID, Instant, String)} — marks a line CONFIRMED;
 *   if it is the last pending line, also transitions instruction status to
 *   COMPLETED or PARTIALLY_COMPLETED.</li>
 *   <li>{@link #skipLine(UUID, Instant, String)} — same semantics for SKIPPED.</li>
 * </ul>
 *
 * <p>{@code isLastLine()} on the returned {@link Transition} drives the
 * application-layer code to call {@code Asn.completePutaway()} and write the
 * {@code inbound.putaway.completed} outbox row in the same TX.
 */
public class PutawayInstruction {

    private final UUID id;
    private final UUID asnId;
    private final UUID warehouseId;
    private final String plannedBy;
    private PutawayInstructionStatus status;
    private long version;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    private final List<PutawayLine> lines;

    public PutawayInstruction(UUID id, UUID asnId, UUID warehouseId, String plannedBy,
                              PutawayInstructionStatus status, long version,
                              Instant createdAt, String createdBy,
                              Instant updatedAt, String updatedBy,
                              List<PutawayLine> lines) {
        this.id = id;
        this.asnId = asnId;
        this.warehouseId = warehouseId;
        this.plannedBy = plannedBy;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.lines = new ArrayList<>(lines);
    }

    /**
     * Factory for a brand-new instruction. Lines must be pre-built (so the
     * caller assigns ids and validates qty/lot/location upstream).
     */
    public static PutawayInstruction createNew(UUID id, UUID asnId, UUID warehouseId,
                                               String plannedBy, Instant now,
                                               List<PutawayLine> lines) {
        return new PutawayInstruction(id, asnId, warehouseId, plannedBy,
                PutawayInstructionStatus.PENDING, 0L,
                now, plannedBy, now, plannedBy, lines);
    }

    /**
     * Marks the line CONFIRMED. If it was the last pending line, the
     * instruction transitions to COMPLETED (all confirmed) or
     * PARTIALLY_COMPLETED (some skipped).
     *
     * @throws PutawayLineNotFoundException if {@code lineId} is unknown to this instruction
     * @throws StateTransitionInvalidException if the line is not PENDING
     */
    public Transition confirmLine(UUID lineId, Instant now, String actorId) {
        PutawayLine line = findLineOrThrow(lineId);
        line.confirm();
        return progressInstruction(now, actorId);
    }

    /**
     * Marks the line SKIPPED. Same last-line behaviour as
     * {@link #confirmLine(UUID, Instant, String)}.
     */
    public Transition skipLine(UUID lineId, Instant now, String actorId) {
        PutawayLine line = findLineOrThrow(lineId);
        line.skip();
        return progressInstruction(now, actorId);
    }

    private Transition progressInstruction(Instant now, String actorId) {
        long pending = lines.stream().filter(PutawayLine::isPending).count();
        boolean isLastLine = pending == 0L;
        this.updatedAt = now;
        this.updatedBy = actorId;
        if (isLastLine) {
            boolean anySkipped = lines.stream().anyMatch(PutawayLine::isSkipped);
            this.status = anySkipped
                    ? PutawayInstructionStatus.PARTIALLY_COMPLETED
                    : PutawayInstructionStatus.COMPLETED;
        } else if (status == PutawayInstructionStatus.PENDING) {
            this.status = PutawayInstructionStatus.IN_PROGRESS;
        }
        return new Transition(isLastLine);
    }

    private PutawayLine findLineOrThrow(UUID lineId) {
        return lines.stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new PutawayLineNotFoundException(lineId));
    }

    public PutawayLine getLine(UUID lineId) {
        return findLineOrThrow(lineId);
    }

    public int totalLineCount() {
        return lines.size();
    }

    public long confirmedLineCount() {
        return lines.stream().filter(PutawayLine::isConfirmed).count();
    }

    public long skippedLineCount() {
        return lines.stream().filter(PutawayLine::isSkipped).count();
    }

    public List<PutawayLine> confirmedLines() {
        return lines.stream().filter(PutawayLine::isConfirmed).toList();
    }

    public UUID getId() { return id; }
    public UUID getAsnId() { return asnId; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getPlannedBy() { return plannedBy; }
    public PutawayInstructionStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public List<PutawayLine> getLines() { return Collections.unmodifiableList(lines); }

    /**
     * Result of {@link #confirmLine(UUID, Instant, String)} or
     * {@link #skipLine(UUID, Instant, String)}.
     *
     * @param isLastLine {@code true} if the line just resolved was the last
     *                   pending one; signals that the application service should
     *                   call {@code Asn.completePutaway()} and emit the
     *                   {@code inbound.putaway.completed} event in the same TX
     */
    public record Transition(boolean isLastLine) {}
}
