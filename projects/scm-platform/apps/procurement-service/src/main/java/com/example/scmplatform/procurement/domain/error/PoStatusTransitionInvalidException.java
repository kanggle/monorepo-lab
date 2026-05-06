package com.example.scmplatform.procurement.domain.error;

import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import lombok.Getter;

/**
 * Thrown when a PO state transition is rejected by the state machine.
 *
 * <p>Mapped to HTTP 422 {@code PO_STATUS_TRANSITION_INVALID} by the controller
 * advice. Carries the {@code from} / {@code to} / {@code actor} so the API
 * layer can surface a useful error envelope.
 */
@Getter
public class PoStatusTransitionInvalidException extends RuntimeException {

    private final PoStatus from;
    private final PoStatus to;
    private final ActorType actor;

    public PoStatusTransitionInvalidException(PoStatus from, PoStatus to, ActorType actor) {
        super("Invalid PO status transition: " + from + " -> " + to + " by " + actor);
        this.from = from;
        this.to = to;
        this.actor = actor;
    }
}
