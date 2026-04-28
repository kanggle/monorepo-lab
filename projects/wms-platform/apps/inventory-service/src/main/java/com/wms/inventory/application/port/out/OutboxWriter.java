package com.wms.inventory.application.port.out;

import com.wms.inventory.domain.event.InventoryDomainEvent;

/**
 * Writes one row to {@code inventory_outbox} per domain event. Called inside
 * the same {@code @Transactional} boundary as the originating mutation, so
 * the event row commits or rolls back atomically with the state change (T3).
 *
 * <p>The publisher process polls the table and forwards rows to Kafka — that
 * is implemented by {@code OutboxPublisher} and is independent of this write
 * path.
 */
public interface OutboxWriter {

    void write(InventoryDomainEvent event);
}
