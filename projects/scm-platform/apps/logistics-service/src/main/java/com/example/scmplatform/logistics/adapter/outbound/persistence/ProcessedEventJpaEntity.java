package com.example.scmplatform.logistics.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Consumer-idempotency marker (T8). Package-private. Written by the BE-044 seam consumer. */
@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "source_topic", nullable = false)
    private String sourceTopic;
}
