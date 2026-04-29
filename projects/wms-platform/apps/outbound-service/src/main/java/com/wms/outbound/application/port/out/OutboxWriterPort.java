package com.wms.outbound.application.port.out;

/**
 * Out-port stub for the {@code outbound_outbox} table writer.
 *
 * <p>Per TASK-BE-034 scope: the table is created (V6 migration), this port is
 * declared, and the implementation is a no-op stub — actual outbox row writes
 * are delivered by TASK-BE-035 alongside the first real domain mutation
 * ({@code ReceiveOrderUseCase}).
 *
 * <p>Future shape (TASK-BE-035+): {@code void write(OutboundDomainEvent event)}
 * with {@code @Transactional(propagation = MANDATORY)} so the outbox row joins
 * the use-case's TX (T3 outbox pattern).
 */
public interface OutboxWriterPort {
    // Stub — methods land in TASK-BE-035. Bean exists so future ports can
    // depend on it via @Autowired without changing signatures.
}
