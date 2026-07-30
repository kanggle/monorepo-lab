package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, String> {
    boolean existsByEventId(String eventId);
}
