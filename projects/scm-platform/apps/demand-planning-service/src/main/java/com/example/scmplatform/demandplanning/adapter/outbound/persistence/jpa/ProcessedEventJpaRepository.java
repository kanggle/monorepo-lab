package com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventJpaRepository
        extends JpaRepository<ProcessedEventJpaEntity, UUID> {

    boolean existsByEventId(UUID eventId);
}
