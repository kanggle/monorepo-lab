package com.wms.inbound.application.port.out;

/**
 * Out-port stub for the {@code inbound_outbox} table writer.
 *
 * <p>Per TASK-BE-029 scope: the table is created (V5 migration), this port is
 * declared, and the implementation is a no-op — actual outbox row writes are
 * delivered by TASK-BE-030 alongside the first real domain mutation
 * ({@code ReceiveAsnUseCase}).
 *
 * <p>Future shape (TASK-BE-030+): {@code void write(InboundDomainEvent event)}
 * with {@code @Transactional(propagation = MANDATORY)} so the outbox row joins
 * the use-case's TX (T3 outbox pattern).
 */
public interface OutboxWriter {
    // Stub — methods land in TASK-BE-030. Bean exists so future ports can
    // depend on it via @Autowired without changing signatures.
}
