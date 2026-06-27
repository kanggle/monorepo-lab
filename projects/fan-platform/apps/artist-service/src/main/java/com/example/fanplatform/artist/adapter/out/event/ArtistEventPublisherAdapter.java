package com.example.fanplatform.artist.adapter.out.event;

import com.example.common.id.UuidV7;
import com.example.fanplatform.artist.adapter.out.persistence.ArtistOutboxJpaEntity;
import com.example.fanplatform.artist.adapter.out.persistence.ArtistOutboxJpaRepository;
import com.example.fanplatform.artist.application.port.out.ArtistEventPublisher;
import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.group.ArtistGroup;
import com.example.fanplatform.artist.domain.group.GroupRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * artist-service outbox write path (TASK-FAN-BE-022, outbox v2).
 *
 * <p>Persists one {@link ArtistOutboxJpaEntity} ({@code artist_outbox} table)
 * per domain event inside the caller's transaction, so the business mutation and
 * the outbox row commit atomically. {@code ArtistOutboxPublisher} drains the
 * table to Kafka.
 *
 * <p>Replaces the v1 path (this adapter previously extended the lib
 * {@code BaseEventPublisher} → {@code OutboxWriter} → {@code OutboxJpaEntity},
 * server-assigned {@code BIGSERIAL}, {@code status} string). <b>Wire is preserved
 * exactly</b>: the Kafka record <b>value</b> is the canonical 7-field envelope
 * JSON built here in the same field order the lib {@code BaseEventPublisher.writeEvent}
 * used — byte-identical; per-event {@code payload} maps (incl. the {@code base()}
 * helper and the conditional {@code reason}/{@code debutDate} puts) copied
 * verbatim; {@code aggregate_id} becomes the Kafka key (partition_key null →
 * relay falls back to aggregateId); the fresh UUIDv7 is both the envelope
 * {@code eventId} and the row PK.
 *
 * <p>The write-side {@code artist_registered_total} counter (incremented in
 * {@link #publishArtistRegistered}) is preserved verbatim from the v1 adapter.
 */
@Component
public class ArtistEventPublisherAdapter implements ArtistEventPublisher {

    static final String AGGREGATE_TYPE_ARTIST = "artist";
    static final String AGGREGATE_TYPE_GROUP = "artist_group";
    static final String SOURCE = "fan-platform-artist-service";
    private static final int SCHEMA_VERSION = 1;

    public static final String EVENT_ARTIST_REGISTERED = "artist.registered";
    public static final String EVENT_ARTIST_PUBLISHED = "artist.published";
    public static final String EVENT_ARTIST_UPDATED = "artist.updated";
    public static final String EVENT_ARTIST_ARCHIVED = "artist.archived";
    public static final String EVENT_ARTIST_GROUP_CREATED = "artist.group_created";
    public static final String EVENT_ARTIST_GROUP_MEMBER_CHANGED = "artist.group_member_changed";

    private final ArtistOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Counter registeredCounter;

    public ArtistEventPublisherAdapter(ArtistOutboxJpaRepository outboxRepository,
                                       ObjectMapper objectMapper,
                                       Clock clock,
                                       MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.registeredCounter = Counter.builder("artist_registered_total")
                .description("Number of artist.registered events appended to the outbox.")
                .register(meterRegistry);
    }

    @Override
    public void publishArtistRegistered(Artist a, String registeredBy) {
        Map<String, Object> payload = base(a.getId().value(), a.getTenantId());
        payload.put("artistType", a.getArtistType().name());
        payload.put("stageName", a.getProfile().stageName());
        payload.put("registeredBy", registeredBy);
        payload.put("occurredAt", a.getCreatedAt().toString());
        writeEvent(AGGREGATE_TYPE_ARTIST, a.getId().value(), EVENT_ARTIST_REGISTERED, payload);
        registeredCounter.increment();
    }

    @Override
    public void publishArtistPublished(Artist a) {
        Map<String, Object> payload = base(a.getId().value(), a.getTenantId());
        payload.put("publishedAt", String.valueOf(a.getPublishedAt()));
        writeEvent(AGGREGATE_TYPE_ARTIST, a.getId().value(), EVENT_ARTIST_PUBLISHED, payload);
    }

    @Override
    public void publishArtistUpdated(ArtistId artistId, String tenantId, List<String> changedFields,
                                     String updatedBy, Instant occurredAt) {
        Map<String, Object> payload = base(artistId.value(), tenantId);
        payload.put("changedFields", changedFields);
        payload.put("updatedBy", updatedBy);
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE_ARTIST, artistId.value(), EVENT_ARTIST_UPDATED, payload);
    }

    @Override
    public void publishArtistArchived(Artist a, String reason, String archivedBy) {
        Map<String, Object> payload = base(a.getId().value(), a.getTenantId());
        payload.put("archivedAt", String.valueOf(a.getArchivedAt()));
        payload.put("archivedBy", archivedBy);
        if (reason != null) payload.put("reason", reason);
        writeEvent(AGGREGATE_TYPE_ARTIST, a.getId().value(), EVENT_ARTIST_ARCHIVED, payload);
    }

    @Override
    public void publishArtistGroupCreated(ArtistGroup g) {
        Map<String, Object> payload = base(g.getId().value(), g.getTenantId());
        payload.put("name", g.getName());
        if (g.getDebutDate() != null) payload.put("debutDate", g.getDebutDate().toString());
        writeEvent(AGGREGATE_TYPE_GROUP, g.getId().value(), EVENT_ARTIST_GROUP_CREATED, payload);
    }

    @Override
    public void publishArtistGroupMemberChanged(ArtistGroup g, ArtistId artistId,
                                                GroupRole role, MemberChangeAction action,
                                                Instant occurredAt) {
        Map<String, Object> payload = base(g.getId().value(), g.getTenantId());
        payload.put("artistId", artistId.value());
        payload.put("role", role.name());
        payload.put("action", action.name());
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE_GROUP, g.getId().value(), EVENT_ARTIST_GROUP_MEMBER_CHANGED, payload);
    }

    private static Map<String, Object> base(String aggregateId, String tenantId) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("aggregateId", aggregateId);
        p.put("tenantId", tenantId);
        return p;
    }

    /**
     * Wrap the payload in the canonical 7-field envelope (v1 shape, same field
     * order as the lib {@code BaseEventPublisher.writeEvent}), serialise to JSON,
     * and persist a pending {@code artist_outbox} row in the caller's
     * transaction. The fresh UUIDv7 doubles as the envelope {@code eventId} and
     * the row PK.
     */
    private void writeEvent(String aggregateType, String aggregateId,
                            String eventType, Map<String, Object> payload) {
        UUID eventId = UuidV7.randomUuid();
        Instant occurredAt = Instant.now(clock);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("source", SOURCE);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("partitionKey", aggregateId);
        envelope.put("payload", payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise " + eventType + " outbox envelope", e);
        }

        outboxRepository.save(new ArtistOutboxJpaEntity(
                eventId, eventType, aggregateType, aggregateId,
                null, // partition_key: publisher falls back to aggregateId (the v1 Kafka key)
                json, occurredAt));
    }
}
