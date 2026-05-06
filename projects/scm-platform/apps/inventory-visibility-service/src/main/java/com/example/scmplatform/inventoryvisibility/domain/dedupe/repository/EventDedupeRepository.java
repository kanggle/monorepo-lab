package com.example.scmplatform.inventoryvisibility.domain.dedupe.repository;

import com.example.scmplatform.inventoryvisibility.domain.dedupe.EventDedupeRecord;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain port for event idempotency records (T8).
 */
public interface EventDedupeRepository {

    boolean existsByEventId(UUID eventId);

    Optional<EventDedupeRecord> findByEventId(UUID eventId);

    EventDedupeRecord save(EventDedupeRecord record);
}
