package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "node_staleness")
@Getter
@Setter
@NoArgsConstructor
public class NodeStalenessJpaEntity {

    @Id
    @Column(name = "node_id", nullable = false, length = 36)
    private String nodeId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @Column(name = "last_event_id", length = 36)
    private String lastEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "staleness_status", nullable = false, length = 20)
    private StalenessStatusJpa stalenessStatus;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    public enum StalenessStatusJpa {
        FRESH, STALE, UNREACHABLE
    }
}
