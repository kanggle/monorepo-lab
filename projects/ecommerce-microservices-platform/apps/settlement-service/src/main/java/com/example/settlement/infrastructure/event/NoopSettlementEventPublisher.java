package com.example.settlement.infrastructure.event;

import com.example.settlement.application.event.SettlementPeriodClosedEvent;
import com.example.settlement.application.port.SettlementEventPublisher;
import lombok.extern.slf4j.Slf4j;

/**
 * No-op {@link SettlementEventPublisher} for the {@code standalone} (H2, no Kafka)
 * profile — the period close still works locally, it just does not relay the event.
 * Clearly marks the drop in logs (no green-washing). The transactional outbox row is
 * not written either (standalone has no dispatcher to drain it).
 */
@Slf4j
public class NoopSettlementEventPublisher implements SettlementEventPublisher {

    @Override
    public void publishPeriodClosed(SettlementPeriodClosedEvent event) {
        log.info("[standalone] settlement.period.closed.v1 NOT relayed (no Kafka/outbox): periodId={}",
                event.payload().periodId());
    }
}
