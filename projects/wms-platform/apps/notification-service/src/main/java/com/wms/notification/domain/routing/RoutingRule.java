package com.wms.notification.domain.routing;

import com.wms.notification.domain.alert.AlertEnvelope;
import com.wms.notification.domain.alert.AlertSeverity;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-event-type rule that decides whether and where an inbound event
 * becomes a notification.
 *
 * <p>Read-mostly aggregate. v1: seeded by Flyway. v2: {@code admin-service}
 * exposes CRUD and the aggregate becomes write-shaped.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>One enabled rule per {@code eventType} (T6 — storage UNIQUE
 *       partial index enforces it).</li>
 *   <li>{@code channelTargets} non-empty when {@code enabled = true}.</li>
 * </ul>
 */
public final class RoutingRule {

    private final UUID id;
    private final String eventType;
    private final RoutingMatcher matcher;
    private final List<ChannelTarget> channelTargets;
    private final AlertSeverity severity;
    private final boolean enabled;
    private final Instant createdAt;
    private final Instant updatedAt;

    public RoutingRule(UUID id,
                       String eventType,
                       RoutingMatcher matcher,
                       List<ChannelTarget> channelTargets,
                       AlertSeverity severity,
                       boolean enabled,
                       Instant createdAt,
                       Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.channelTargets = List.copyOf(Objects.requireNonNull(channelTargets, "channelTargets"));
        this.severity = Objects.requireNonNull(severity, "severity");
        this.enabled = enabled;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (enabled && this.channelTargets.isEmpty()) {
            throw new IllegalArgumentException(
                    "enabled rule must have at least one channelTarget: " + eventType);
        }
    }

    /** Apply the matcher; only enabled rules are considered to match. */
    public boolean matches(AlertEnvelope envelope) {
        return enabled
                && eventType.equals(envelope.eventType())
                && matcher.matches(envelope);
    }

    public UUID id() { return id; }
    public String eventType() { return eventType; }
    public RoutingMatcher matcher() { return matcher; }
    public List<ChannelTarget> channelTargets() { return channelTargets; }
    public AlertSeverity severity() { return severity; }
    public boolean enabled() { return enabled; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
