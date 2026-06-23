package com.example.product.infrastructure.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Idempotent-consumer dedupe store for the reservation saga consumers (TASK-BE-428). */
public interface ReservationProcessedEventRepository
        extends JpaRepository<ReservationProcessedEventEntity, UUID> {
}
