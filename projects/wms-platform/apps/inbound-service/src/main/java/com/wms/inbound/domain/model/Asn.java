package com.wms.inbound.domain.model;

import com.wms.inbound.domain.exception.AsnAlreadyClosedException;
import com.wms.inbound.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Asn {

    private static final java.util.Set<AsnStatus> CANCELLABLE_STATUSES =
            java.util.EnumSet.of(AsnStatus.CREATED, AsnStatus.INSPECTING);
    private static final java.util.Set<AsnStatus> TERMINAL_STATUSES =
            java.util.EnumSet.of(AsnStatus.IN_PUTAWAY, AsnStatus.PUTAWAY_DONE,
                    AsnStatus.CLOSED, AsnStatus.CANCELLED);

    private final UUID id;
    private final String asnNo;
    private final AsnSource source;
    private final UUID supplierPartnerId;
    private final UUID warehouseId;
    private final LocalDate expectedArriveDate;
    private final String notes;
    private AsnStatus status;
    private long version;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    private final List<AsnLine> lines;

    public Asn(UUID id, String asnNo, AsnSource source,
               UUID supplierPartnerId, UUID warehouseId,
               LocalDate expectedArriveDate, String notes,
               AsnStatus status, long version,
               Instant createdAt, String createdBy,
               Instant updatedAt, String updatedBy,
               List<AsnLine> lines) {
        this.id = id;
        this.asnNo = asnNo;
        this.source = source;
        this.supplierPartnerId = supplierPartnerId;
        this.warehouseId = warehouseId;
        this.expectedArriveDate = expectedArriveDate;
        this.notes = notes;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.lines = new ArrayList<>(lines);
    }

    public void startInspection(Instant now, String actorId) {
        if (status != AsnStatus.CREATED) {
            throw new StateTransitionInvalidException(status.name(), AsnStatus.INSPECTING.name());
        }
        this.status = AsnStatus.INSPECTING;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void completeInspection(Instant now, String actorId) {
        if (status != AsnStatus.INSPECTING) {
            throw new StateTransitionInvalidException(status.name(), AsnStatus.INSPECTED.name());
        }
        this.status = AsnStatus.INSPECTED;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void instructPutaway(Instant now, String actorId) {
        if (status != AsnStatus.INSPECTED) {
            throw new StateTransitionInvalidException(status.name(), AsnStatus.IN_PUTAWAY.name());
        }
        this.status = AsnStatus.IN_PUTAWAY;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void completePutaway(Instant now, String actorId) {
        if (status != AsnStatus.IN_PUTAWAY) {
            throw new StateTransitionInvalidException(status.name(), AsnStatus.PUTAWAY_DONE.name());
        }
        this.status = AsnStatus.PUTAWAY_DONE;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void close(Instant now, String actorId) {
        if (status != AsnStatus.PUTAWAY_DONE) {
            throw new StateTransitionInvalidException(status.name(), AsnStatus.CLOSED.name());
        }
        this.status = AsnStatus.CLOSED;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void cancel(String reason, Instant now, String actorId) {
        if (TERMINAL_STATUSES.contains(status)) {
            throw new AsnAlreadyClosedException(id, status.name());
        }
        if (!CANCELLABLE_STATUSES.contains(status)) {
            throw new StateTransitionInvalidException(status.name(), AsnStatus.CANCELLED.name());
        }
        this.status = AsnStatus.CANCELLED;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public String getAsnNo() { return asnNo; }
    public AsnSource getSource() { return source; }
    public UUID getSupplierPartnerId() { return supplierPartnerId; }
    public UUID getWarehouseId() { return warehouseId; }
    public LocalDate getExpectedArriveDate() { return expectedArriveDate; }
    public String getNotes() { return notes; }
    public AsnStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public List<AsnLine> getLines() { return Collections.unmodifiableList(lines); }
}
