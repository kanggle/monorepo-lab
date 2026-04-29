package com.wms.outbound.adapter.out.event.outbox;

import com.wms.outbound.application.port.out.OutboxWriterPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link OutboxWriterPort}.
 *
 * <p>Per TASK-BE-034 scope: the table is created (V6 migration) and this
 * bean exists so future use-case services can declare the dependency.
 * Real outbox row writes land in TASK-BE-035 alongside
 * {@code ReceiveOrderUseCase}.
 */
@Component
public class StubOutboxWriterAdapter implements OutboxWriterPort {

    private static final Logger log = LoggerFactory.getLogger(StubOutboxWriterAdapter.class);

    /**
     * Stub log call exposed for use-case tests in later tasks. Keeps the
     * "Outbox write stub" log line shape stable.
     */
    public void logStubWrite(String eventType) {
        log.info("Outbox write stub: {}", eventType);
    }
}
