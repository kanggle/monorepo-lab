package com.example.erp.readmodel.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Consumer dedupe / processing-provenance row ({@code processed_events}, T8).
 * Distinct from libs/java-messaging's {@code ProcessedEventJpaEntity} — that
 * outbox auto-config is excluded (read-model is no-outbox), so this service owns
 * its own dedupe table with the read-model column set
 * ({@code event_id}, {@code topic}, {@code aggregate_id}, {@code processed_at}).
 */
@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
