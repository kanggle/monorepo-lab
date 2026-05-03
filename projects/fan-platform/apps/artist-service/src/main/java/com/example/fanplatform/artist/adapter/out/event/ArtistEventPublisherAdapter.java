package com.example.fanplatform.artist.adapter.out.event;

import com.example.fanplatform.artist.application.port.out.ArtistEventPublisher;
import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import com.example.fanplatform.artist.domain.group.ArtistGroup;
import com.example.fanplatform.artist.domain.group.GroupRole;
import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outbox-backed publisher for the {@code artist.*} event family.
 *
 * <p>Inherits the standard envelope shape from {@link BaseEventPublisher}
 * ({@code eventId, eventType, source, occurredAt, schemaVersion, partitionKey,
 * payload}) — identical to community-service so consumers can treat both
 * services uniformly.
 *
 * <p>Topic mapping (resolved by {@link ArtistOutboxPollingScheduler}):
 * <ul>
 *   <li>{@code artist.registered}            → {@code artist.registered.v1}</li>
 *   <li>{@code artist.published}             → {@code artist.published.v1}</li>
 *   <li>{@code artist.updated}               → {@code artist.updated.v1}</li>
 *   <li>{@code artist.archived}              → {@code artist.archived.v1}</li>
 *   <li>{@code artist.group_created}         → {@code artist.group_created.v1}</li>
 *   <li>{@code artist.group_member_changed}  → {@code artist.group_member_changed.v1}</li>
 * </ul>
 */
@Component
public class ArtistEventPublisherAdapter extends BaseEventPublisher implements ArtistEventPublisher {

    static final String AGGREGATE_TYPE_ARTIST = "artist";
    static final String AGGREGATE_TYPE_GROUP = "artist_group";
    static final String SOURCE = "fan-platform-artist-service";

    public static final String EVENT_ARTIST_REGISTERED = "artist.registered";
    public static final String EVENT_ARTIST_PUBLISHED = "artist.published";
    public static final String EVENT_ARTIST_UPDATED = "artist.updated";
    public static final String EVENT_ARTIST_ARCHIVED = "artist.archived";
    public static final String EVENT_ARTIST_GROUP_CREATED = "artist.group_created";
    public static final String EVENT_ARTIST_GROUP_MEMBER_CHANGED = "artist.group_member_changed";

    private final Counter registeredCounter;

    public ArtistEventPublisherAdapter(OutboxWriter outboxWriter,
                                       ObjectMapper objectMapper,
                                       MeterRegistry meterRegistry) {
        super(outboxWriter, objectMapper);
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
        writeEvent(AGGREGATE_TYPE_ARTIST, a.getId().value(), EVENT_ARTIST_REGISTERED, SOURCE, payload);
        registeredCounter.increment();
    }

    @Override
    public void publishArtistPublished(Artist a) {
        Map<String, Object> payload = base(a.getId().value(), a.getTenantId());
        payload.put("publishedAt", String.valueOf(a.getPublishedAt()));
        writeEvent(AGGREGATE_TYPE_ARTIST, a.getId().value(), EVENT_ARTIST_PUBLISHED, SOURCE, payload);
    }

    @Override
    public void publishArtistUpdated(ArtistId artistId, String tenantId, List<String> changedFields,
                                     String updatedBy, Instant occurredAt) {
        Map<String, Object> payload = base(artistId.value(), tenantId);
        payload.put("changedFields", changedFields);
        payload.put("updatedBy", updatedBy);
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE_ARTIST, artistId.value(), EVENT_ARTIST_UPDATED, SOURCE, payload);
    }

    @Override
    public void publishArtistArchived(Artist a, String reason, String archivedBy) {
        Map<String, Object> payload = base(a.getId().value(), a.getTenantId());
        payload.put("archivedAt", String.valueOf(a.getArchivedAt()));
        payload.put("archivedBy", archivedBy);
        if (reason != null) payload.put("reason", reason);
        writeEvent(AGGREGATE_TYPE_ARTIST, a.getId().value(), EVENT_ARTIST_ARCHIVED, SOURCE, payload);
    }

    @Override
    public void publishArtistGroupCreated(ArtistGroup g) {
        Map<String, Object> payload = base(g.getId().value(), g.getTenantId());
        payload.put("name", g.getName());
        if (g.getDebutDate() != null) payload.put("debutDate", g.getDebutDate().toString());
        writeEvent(AGGREGATE_TYPE_GROUP, g.getId().value(), EVENT_ARTIST_GROUP_CREATED, SOURCE, payload);
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
        writeEvent(AGGREGATE_TYPE_GROUP, g.getId().value(), EVENT_ARTIST_GROUP_MEMBER_CHANGED, SOURCE, payload);
    }

    static void touchArtistTypeForCompiler(ArtistType ignored) {
        // intentionally unused — keeps the import path active for IDE refactor safety.
    }

    private static Map<String, Object> base(String aggregateId, String tenantId) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("aggregateId", aggregateId);
        p.put("tenantId", tenantId);
        return p;
    }
}
