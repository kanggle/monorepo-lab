package com.wms.outbound.domain.exception;

import java.util.UUID;

/**
 * Raised when a pick confirmation names a lot different from the order line's
 * planned concrete lot. Mapped to {@code 422} with code
 * {@code LOT_SUBSTITUTION_NOT_ALLOWED} (TASK-MONO-724).
 *
 * <p>Inventory reserved the planned lot's row; v1 has no re-reservation step, so a
 * substituted lot riding {@code outbound.shipping.confirmed} could not be matched to
 * the reservation downstream. An any-lot order line (planned lot {@code null}) is
 * not a substitution — the operator binds the physical lot at confirmation.
 */
public class LotSubstitutionNotAllowedException extends OutboundDomainException {

    private final UUID orderLineId;
    private final UUID plannedLotId;
    private final UUID confirmedLotId;

    public LotSubstitutionNotAllowedException(UUID orderLineId, UUID plannedLotId, UUID confirmedLotId) {
        super("lot substitution is not allowed on orderLineId=" + orderLineId
                + " planned=" + plannedLotId + " confirmed=" + confirmedLotId);
        this.orderLineId = orderLineId;
        this.plannedLotId = plannedLotId;
        this.confirmedLotId = confirmedLotId;
    }

    public UUID getOrderLineId() {
        return orderLineId;
    }

    public UUID getPlannedLotId() {
        return plannedLotId;
    }

    public UUID getConfirmedLotId() {
        return confirmedLotId;
    }

    @Override
    public String errorCode() {
        return "LOT_SUBSTITUTION_NOT_ALLOWED";
    }
}
