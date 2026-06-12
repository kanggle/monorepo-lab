package com.example.finance.ledger.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Consumer dedupe / processing-provenance row ({@code processed_events}, F1/T8).
 * Keyed on the signed envelope {@code eventId}; a duplicate is skipped without
 * mutation so re-delivery posts at most one entry. Distinct from
 * libs/java-messaging's outbox {@code ProcessedEventJpaEntity} — that outbox
 * auto-config is excluded (ledger is a terminal consumer, no outbox), so this
 * service owns its own dedupe table.
 */
@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "source_transaction_id", nullable = false, length = 64)
    private String sourceTransactionId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
