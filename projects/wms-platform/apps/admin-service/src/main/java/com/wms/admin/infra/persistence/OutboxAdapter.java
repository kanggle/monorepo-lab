package com.wms.admin.infra.persistence;

import com.example.common.id.UuidV7;
import com.wms.admin.application.port.OutboxPort;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one row to {@code admin_outbox} per emitted event. Caller's
 * transaction (T3 — atomic with the aggregate row).
 */
@Component
public class OutboxAdapter implements OutboxPort {

    private static final String EVENT_VERSION = "v1";

    private final AdminOutboxJpaRepository repository;
    private final Clock clock;

    public OutboxAdapter(AdminOutboxJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, String aggregateId, String eventType,
                       String payload, String partitionKey) {
        AdminOutboxJpaEntity row = new AdminOutboxJpaEntity(
                UuidV7.randomUuid(),
                aggregateType,
                aggregateId,
                eventType,
                EVENT_VERSION,
                payload,
                partitionKey,
                clock.instant());
        repository.save(row);
    }
}
