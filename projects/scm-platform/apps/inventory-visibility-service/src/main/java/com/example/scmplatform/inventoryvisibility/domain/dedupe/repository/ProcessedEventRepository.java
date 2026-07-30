package com.example.scmplatform.inventoryvisibility.domain.dedupe.repository;

import com.example.scmplatform.inventoryvisibility.domain.dedupe.ProcessedEventRecord;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain port for event idempotency records (T8).
 */
public interface ProcessedEventRepository {

    boolean existsByEventId(UUID eventId);

    Optional<ProcessedEventRecord> findByEventId(UUID eventId);

    ProcessedEventRecord save(ProcessedEventRecord record);
}
