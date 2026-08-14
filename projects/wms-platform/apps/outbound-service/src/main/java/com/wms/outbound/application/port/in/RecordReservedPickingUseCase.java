package com.wms.outbound.application.port.in;

import com.wms.outbound.application.command.RecordReservedPickingCommand;

/**
 * Creates the {@code PickingRequest} row for a saga whose reservation just
 * succeeded (ADR-MONO-066, option B).
 *
 * <p><b>Why this in-port exists at all.</b> Until TASK-BE-586 nothing in
 * production ever called {@code PickingPersistencePort.save} — measured: 0
 * production callers, 2 test callers. The rows existed only inside tests, which
 * meant every downstream step (confirm picking → pack → ship) was verified on a
 * state the product could not produce, and no test could see that. The whole
 * pick-pack-ship path was unreachable in production and
 * {@code admin_shipment_summary} was structurally empty.
 */
public interface RecordReservedPickingUseCase {

    /**
     * Idempotent: does nothing if the order already has a picking request.
     */
    void recordReservedPicking(RecordReservedPickingCommand command);
}
