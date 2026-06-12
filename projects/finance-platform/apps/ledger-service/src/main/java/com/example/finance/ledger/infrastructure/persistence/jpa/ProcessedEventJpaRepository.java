package com.example.finance.ledger.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the consumer dedupe store ({@code processed_events}). */
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, String> {
}
