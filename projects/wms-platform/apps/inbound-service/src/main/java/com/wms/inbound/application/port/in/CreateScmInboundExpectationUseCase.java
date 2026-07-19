package com.wms.inbound.application.port.in;

import com.wms.inbound.application.command.CreateScmInboundExpectationCommand;
import java.util.Optional;
import java.util.UUID;

/**
 * Create an inbound expectation (ASN) from an scm confirmed-PO event (ADR-MONO-050 D3/D5).
 *
 * <p>Warehouse-addressed: the destination is resolved from the event's
 * {@code destinationWarehouseCode}, so single- and multi-warehouse deployments share one code
 * path (no branching on warehouse count). Fail-closed on unknown/inactive master references and
 * non-{@code WMS_WAREHOUSE} destination node types.
 */
public interface CreateScmInboundExpectationUseCase {

    /**
     * @return the created ASN id, or {@link Optional#empty()} when the event was a business
     *         duplicate ({@code (poNumber, line)} already open, D6.2) and no ASN was created.
     */
    Optional<UUID> create(CreateScmInboundExpectationCommand command);
}
