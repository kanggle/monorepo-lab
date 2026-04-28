package com.wms.inbound.adapter.out.messaging;

import com.wms.inbound.application.port.out.OutboxWriter;
import org.springframework.stereotype.Component;

/**
 * Stub adapter for {@link OutboxWriter}.
 *
 * <p>TASK-BE-029 scope creates the {@code inbound_outbox} table and the port,
 * but no domain mutation writes outbox rows yet. The real implementation lands
 * in TASK-BE-030 alongside {@code ReceiveAsnUseCase}.
 *
 * <p>The bean exists so future use-case services can declare an
 * {@link OutboxWriter} dependency without rewiring at the moment of first use.
 */
@Component
public class OutboxWriterAdapter implements OutboxWriter {
    // No-op until TASK-BE-030.
}
