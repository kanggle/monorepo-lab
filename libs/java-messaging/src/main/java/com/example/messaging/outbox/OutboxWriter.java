package com.example.messaging.outbox;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxJpaRepository outboxJpaRepository;

    public void save(String aggregateType, String aggregateId,
                     String eventType, String payload) {
        OutboxJpaEntity entity = OutboxJpaEntity.create(aggregateType, aggregateId, eventType, payload);
        outboxJpaRepository.save(entity);
    }
}
