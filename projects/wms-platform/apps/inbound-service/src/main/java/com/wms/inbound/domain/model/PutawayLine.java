package com.wms.inbound.domain.model;

import com.wms.inbound.domain.exception.StateTransitionInvalidException;
import java.util.UUID;

/**
 * Child entity of {@link PutawayInstruction}. Each line is the
 * destination-assignment for a quantity of an inspected ASN line.
 *
 * <p>State machine:
 * <pre>
 *   PENDING ──[confirm]──&gt; CONFIRMED (terminal)
 *   PENDING ──[skip]────&gt; SKIPPED   (terminal)
 * </pre>
 */
public class PutawayLine {

    private final UUID id;
    private final UUID putawayInstructionId;
    private final UUID asnLineId;
    private final UUID skuId;
    private final UUID lotId;
    private final String lotNo;
    private final UUID destinationLocationId;
    private final int qtyToPutaway;
    private PutawayLineStatus status;

    public PutawayLine(UUID id, UUID putawayInstructionId, UUID asnLineId,
                       UUID skuId, UUID lotId, String lotNo,
                       UUID destinationLocationId, int qtyToPutaway,
                       PutawayLineStatus status) {
        this.id = id;
        this.putawayInstructionId = putawayInstructionId;
        this.asnLineId = asnLineId;
        this.skuId = skuId;
        this.lotId = lotId;
        this.lotNo = lotNo;
        this.destinationLocationId = destinationLocationId;
        this.qtyToPutaway = qtyToPutaway;
        this.status = status;
    }

    public void confirm() {
        if (status != PutawayLineStatus.PENDING) {
            throw new StateTransitionInvalidException(status.name(), PutawayLineStatus.CONFIRMED.name());
        }
        this.status = PutawayLineStatus.CONFIRMED;
    }

    public void skip() {
        if (status != PutawayLineStatus.PENDING) {
            throw new StateTransitionInvalidException(status.name(), PutawayLineStatus.SKIPPED.name());
        }
        this.status = PutawayLineStatus.SKIPPED;
    }

    public boolean isPending() {
        return status == PutawayLineStatus.PENDING;
    }

    public boolean isConfirmed() {
        return status == PutawayLineStatus.CONFIRMED;
    }

    public boolean isSkipped() {
        return status == PutawayLineStatus.SKIPPED;
    }

    public UUID getId() { return id; }
    public UUID getPutawayInstructionId() { return putawayInstructionId; }
    public UUID getAsnLineId() { return asnLineId; }
    public UUID getSkuId() { return skuId; }
    public UUID getLotId() { return lotId; }
    public String getLotNo() { return lotNo; }
    public UUID getDestinationLocationId() { return destinationLocationId; }
    public int getQtyToPutaway() { return qtyToPutaway; }
    public PutawayLineStatus getStatus() { return status; }
}
