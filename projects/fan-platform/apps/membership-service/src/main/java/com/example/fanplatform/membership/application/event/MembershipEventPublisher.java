package com.example.fanplatform.membership.application.event;

import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The ONLY producer path for {@code fan.membership.*} events. Appends to the
 * transactional outbox via {@link BaseEventPublisher} ({@code libs:java-messaging})
 * so the envelope shape ({@code eventId / eventType / source / occurredAt /
 * schemaVersion / partitionKey / payload}) is identical across services.
 *
 * <p>No use case or controller may call {@code OutboxWriter} or Kafka directly —
 * every event MUST flow through this publisher (architecture.md § Boundary rules,
 * AC-8).
 *
 * <p>Topic naming: the envelope's {@code eventType} plus a {@code .v1} suffix
 * (resolved by {@code MembershipOutboxPollingScheduler}).
 */
@Component
public class MembershipEventPublisher extends BaseEventPublisher {

    private static final String AGGREGATE_TYPE = "membership";
    private static final String SOURCE = "fan-platform-membership-service";

    public static final String EVENT_ACTIVATED = "fan.membership.activated";
    public static final String EVENT_CANCELED = "fan.membership.canceled";
    public static final String EVENT_EXPIRED = "fan.membership.expired";

    public MembershipEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    /** Emitted on subscribe → ACTIVE (PG mock approved). */
    public void publishActivated(String membershipId, String tenantId, String accountId,
                                 MembershipTier tier, int planMonths,
                                 Instant validFrom, Instant validTo, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("membershipId", membershipId);
        payload.put("tenantId", tenantId);
        payload.put("accountId", accountId);
        payload.put("tier", tier.name());
        payload.put("planMonths", planMonths);
        payload.put("validFrom", validFrom.toString());
        payload.put("validTo", validTo.toString());
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE, membershipId, EVENT_ACTIVATED, SOURCE, payload);
    }

    /** Emitted on ACTIVE → CANCELED. A re-cancel of a CANCELED membership emits nothing. */
    public void publishCanceled(String membershipId, String tenantId, String accountId,
                                MembershipTier tier, String reason,
                                Instant canceledAt, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("membershipId", membershipId);
        payload.put("tenantId", tenantId);
        payload.put("accountId", accountId);
        payload.put("tier", tier.name());
        payload.put("reason", reason);
        payload.put("canceledAt", canceledAt.toString());
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE, membershipId, EVENT_CANCELED, SOURCE, payload);
    }

    /**
     * Emitted once per membership by the expiry sweeper (TASK-FAN-BE-014) when its
     * window has ended. The membership keeps {@code status=ACTIVE} (read-time
     * expiry — Option B); this is a notification trigger, not a lifecycle change.
     */
    public void publishExpired(String membershipId, String tenantId, String accountId,
                               MembershipTier tier, Instant validTo, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("membershipId", membershipId);
        payload.put("tenantId", tenantId);
        payload.put("accountId", accountId);
        payload.put("tier", tier.name());
        payload.put("validTo", validTo.toString());
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE, membershipId, EVENT_EXPIRED, SOURCE, payload);
    }
}
