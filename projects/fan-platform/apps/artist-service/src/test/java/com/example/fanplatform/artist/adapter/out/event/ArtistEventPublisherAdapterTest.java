package com.example.fanplatform.artist.adapter.out.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.fanplatform.artist.adapter.out.persistence.ArtistOutboxJpaEntity;
import com.example.fanplatform.artist.adapter.out.persistence.ArtistOutboxJpaRepository;
import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.artist.ArtistProfile;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import com.example.fanplatform.artist.domain.group.ArtistGroup;
import com.example.fanplatform.artist.domain.group.ArtistGroupId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit test for the {@link ArtistEventPublisherAdapter} write path
 * (TASK-FAN-BE-022, outbox v2).
 *
 * <p>Asserts each domain event persists an {@code artist_outbox} row whose
 * wire-relevant fields are preserved exactly vs the v1 lib
 * {@code BaseEventPublisher.writeEvent}: the canonical 7-field envelope in that
 * field order, the row {@code event_id} reused as the envelope {@code eventId},
 * {@code aggregate_type}/{@code aggregate_id}/{@code event_type} matching the v1
 * call, and {@code partition_key} left null so the relay falls back to
 * {@code aggregateId}. Pins the conditional {@code reason}/{@code debutDate}
 * payload omissions and the preserved {@code artist_registered_total} write-side
 * counter increment.
 */
class ArtistEventPublisherAdapterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T10:15:30Z"), ZoneOffset.UTC);

    private final ArtistOutboxJpaRepository repository = mock(ArtistOutboxJpaRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final ArtistEventPublisherAdapter adapter =
            new ArtistEventPublisherAdapter(repository, objectMapper, CLOCK, registry);

    private static ArtistProfile sampleProfile() {
        return new ArtistProfile("STAGE", "real", LocalDate.of(2020, 1, 1),
                "Agency", "bio", "img/x.jpg");
    }

    private static Artist sampleArtist() {
        return Artist.register(ArtistId.of("a-1"), "fan-platform", ArtistType.SOLO, sampleProfile());
    }

    @Test
    void publishArtistRegistered_persistsV2Row_andIncrementsWriteSideCounter() throws Exception {
        adapter.publishArtistRegistered(sampleArtist(), "admin-1");

        ArtistOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(ArtistEventPublisherAdapter.EVENT_ARTIST_REGISTERED);
        assertThat(row.getAggregateType()).isEqualTo("artist");
        assertThat(row.getAggregateId()).isEqualTo("a-1");
        assertThat(row.getPartitionKey()).isNull();
        assertThat(row.getOccurredAt()).isEqualTo(CLOCK.instant());

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.get("eventId").asText()).isEqualTo(row.getEventId().toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo(ArtistEventPublisherAdapter.EVENT_ARTIST_REGISTERED);
        assertThat(envelope.get("source").asText()).isEqualTo("fan-platform-artist-service");
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("partitionKey").asText()).isEqualTo("a-1");
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("aggregateId").asText()).isEqualTo("a-1");
        assertThat(payload.get("tenantId").asText()).isEqualTo("fan-platform");
        assertThat(payload.get("artistType").asText()).isEqualTo("SOLO");
        assertThat(payload.get("stageName").asText()).isEqualTo("STAGE");
        assertThat(payload.get("registeredBy").asText()).isEqualTo("admin-1");

        // preserved write-side counter
        assertThat(registry.find("artist_registered_total").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void publishArtistArchived_omitsReason_whenNull() throws Exception {
        Artist a = sampleArtist();
        a.publish();
        a.archive();

        adapter.publishArtistArchived(a, null, "admin-1");

        ArtistOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(ArtistEventPublisherAdapter.EVENT_ARTIST_ARCHIVED);
        JsonNode payload = objectMapper.readTree(row.getPayload()).get("payload");
        assertThat(payload.get("archivedBy").asText()).isEqualTo("admin-1");
        assertThat(payload.has("reason")).isFalse();
        // write-side counter only fires on registered
        assertThat(registry.find("artist_registered_total").counter().count()).isEqualTo(0.0d);
    }

    @Test
    void publishArtistArchived_includesReason_whenPresent() throws Exception {
        Artist a = sampleArtist();
        a.publish();
        a.archive();

        adapter.publishArtistArchived(a, "spam", "admin-1");

        ArtistOutboxJpaEntity row = capturedRow();
        JsonNode payload = objectMapper.readTree(row.getPayload()).get("payload");
        assertThat(payload.get("reason").asText()).isEqualTo("spam");
    }

    @Test
    void publishArtistGroupCreated_omitsDebutDate_whenNull() throws Exception {
        ArtistGroup g = ArtistGroup.create(ArtistGroupId.of("g-1"), "fan-platform",
                "Group X", null, "Agency", "img/g.jpg");

        adapter.publishArtistGroupCreated(g);

        ArtistOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(ArtistEventPublisherAdapter.EVENT_ARTIST_GROUP_CREATED);
        assertThat(row.getAggregateType()).isEqualTo("artist_group");
        assertThat(row.getAggregateId()).isEqualTo("g-1");
        JsonNode payload = objectMapper.readTree(row.getPayload()).get("payload");
        assertThat(payload.get("name").asText()).isEqualTo("Group X");
        assertThat(payload.has("debutDate")).isFalse();
    }

    @Test
    void publishArtistGroupCreated_includesDebutDate_whenPresent() throws Exception {
        ArtistGroup g = ArtistGroup.create(ArtistGroupId.of("g-1"), "fan-platform",
                "Group X", LocalDate.of(2020, 1, 1), "Agency", "img/g.jpg");

        adapter.publishArtistGroupCreated(g);

        ArtistOutboxJpaEntity row = capturedRow();
        JsonNode payload = objectMapper.readTree(row.getPayload()).get("payload");
        assertThat(payload.get("debutDate").asText()).isEqualTo("2020-01-01");
    }

    private ArtistOutboxJpaEntity capturedRow() {
        ArgumentCaptor<ArtistOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(ArtistOutboxJpaEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
