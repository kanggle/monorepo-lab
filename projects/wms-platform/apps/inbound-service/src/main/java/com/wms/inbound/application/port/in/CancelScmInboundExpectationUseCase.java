package com.wms.inbound.application.port.in;

import com.wms.inbound.application.command.CancelScmInboundExpectationCommand;

/**
 * Cancel an inbound expectation on an scm PO-cancel event (ADR-MONO-050 D6.3).
 *
 * <p>Marks the matching <em>open</em> (not-yet-received) ASN {@code CANCELLED}; a no-op when the
 * expectation was already received (goods physically in flow) or never created.
 */
public interface CancelScmInboundExpectationUseCase {

    void cancel(CancelScmInboundExpectationCommand command);
}
