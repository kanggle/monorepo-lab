package com.wms.inbound.application.command;

import java.util.UUID;

/**
 * Consumed scm {@code inbound-expected.cancelled.v1} payload (ADR-MONO-050 D6.3), decoded to the
 * subset wms reads. The cancellation is keyed on {@code poNumber} (the business key).
 */
public record CancelScmInboundExpectationCommand(
        UUID poId,
        String poNumber,
        String reason
) {}
