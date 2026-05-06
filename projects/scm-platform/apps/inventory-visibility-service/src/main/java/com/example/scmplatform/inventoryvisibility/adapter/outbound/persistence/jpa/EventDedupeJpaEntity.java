package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "event_dedupe")
@Getter
@Setter
@NoArgsConstructor
public class EventDedupeJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "source_topic", nullable = false, length = 200)
    private String sourceTopic;
}
