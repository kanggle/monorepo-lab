package com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dp_processed_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "source_topic", nullable = false)
    private String sourceTopic;
}
