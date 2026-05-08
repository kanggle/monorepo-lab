package com.wms.notification.adapter.outbound.persistence.jpa.routing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for {@code notification_routing_rule}. Both JSONB columns use
 * {@link JdbcTypeCode SqlTypes.JSON} per the regression-guard learning
 * (TASK-SCM-INT-001b root cause #2 / TASK-SCM-BE-005). The payload is
 * serialised to a {@link String} by the persistence adapter — domain code
 * never sees these JSON strings.
 */
@Entity
@Table(name = "notification_routing_rule")
public class RoutingRuleJpaEntity {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matcher_json", nullable = false, columnDefinition = "jsonb")
    private String matcherJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_targets_json", nullable = false, columnDefinition = "jsonb")
    private String channelTargetsJson;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoutingRuleJpaEntity() {
    }

    public RoutingRuleJpaEntity(UUID id, String eventType, String matcherJson,
                                String channelTargetsJson, String severity, boolean enabled,
                                Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.eventType = eventType;
        this.matcherJson = matcherJson;
        this.channelTargetsJson = channelTargetsJson;
        this.severity = severity;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getMatcherJson() { return matcherJson; }
    public String getChannelTargetsJson() { return channelTargetsJson; }
    public String getSeverity() { return severity; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
