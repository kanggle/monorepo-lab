package com.example.settlement.application.port;

import com.example.settlement.application.event.SettlementPeriodClosedEvent;

/**
 * Outbound application port for publishing settlement domain events. The only
 * implementation appends to the transactional outbox in the same
 * {@code @Transactional} boundary as the period close + payout-row inserts
 * (architecture.md § Outbox). settlement-service publishes exactly one event in this
 * increment, {@code settlement.period.closed.v1}.
 */
public interface SettlementEventPublisher {

    /** Appends {@code settlement.period.closed.v1} to the outbox (co-committed with the close). */
    void publishPeriodClosed(SettlementPeriodClosedEvent event);
}
