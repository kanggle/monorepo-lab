package com.wms.inventory.adapter.in.messaging.masterref;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.inventory.application.port.out.EventDedupePort;
import com.wms.inventory.application.port.out.MasterReadModelWriterPort;
import com.wms.inventory.domain.model.masterref.LocationSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link MasterLocationConsumer} dedupe + version-guard
 * orchestration. The Kafka listener machinery and DB are not exercised — the
 * port contracts are mocked to verify the consumer's wiring.
 */
class MasterLocationConsumerTest {

    private static final UUID LOCATION_ID = UUID.fromString("01910000-0000-7000-8000-000000001001");
    private static final UUID WAREHOUSE_ID = UUID.fromString("01910000-0000-7000-8000-000000000001");
    private static final UUID ZONE_ID = UUID.fromString("01910000-0000-7000-8000-000000000101");
    private static final Instant FIXED_NOW = Instant.parse("2026-04-25T10:00:00Z");

    private MasterReadModelWriterPort writer;
    private EventDedupePort dedupe;
    private MasterLocationConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        writer = mock(MasterReadModelWriterPort.class);
        dedupe = mock(EventDedupePort.class);
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        consumer = new MasterLocationConsumer(new MasterEventParser(objectMapper), writer, dedupe, clock);

        // Default behaviour: dedupe runs the supplied work exactly once
        doAnswer(invocation -> {
            Runnable work = invocation.getArgument(2);
            work.run();
            return EventDedupePort.Outcome.APPLIED;
        }).when(dedupe).process(any(UUID.class), any(String.class), any(Runnable.class));
    }

    @Test
    void appliesCreatedEventThroughWriter() {
        when(writer.upsertLocation(any())).thenReturn(true);
        consumer.handle(buildLocationEvent("master.location.created", "ACTIVE", 0L), "key-1");

        ArgumentCaptor<LocationSnapshot> captor = ArgumentCaptor.forClass(LocationSnapshot.class);
        verify(writer).upsertLocation(captor.capture());
        LocationSnapshot snapshot = captor.getValue();
        assertThat(snapshot.id()).isEqualTo(LOCATION_ID);
        assertThat(snapshot.locationCode()).isEqualTo("WH01-A-01-01-01");
        assertThat(snapshot.warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(snapshot.zoneId()).isEqualTo(ZONE_ID);
        assertThat(snapshot.locationType()).isEqualTo(LocationSnapshot.LocationType.STORAGE);
        assertThat(snapshot.status()).isEqualTo(LocationSnapshot.Status.ACTIVE);
        assertThat(snapshot.cachedAt()).isEqualTo(FIXED_NOW);
        assertThat(snapshot.masterVersion()).isEqualTo(0L);
    }

    @Test
    void mapsDeactivatedToInactiveStatus() {
        when(writer.upsertLocation(any())).thenReturn(true);
        consumer.handle(buildLocationEvent("master.location.deactivated", "INACTIVE", 1L), "key-1");

        ArgumentCaptor<LocationSnapshot> captor = ArgumentCaptor.forClass(LocationSnapshot.class);
        verify(writer).upsertLocation(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(LocationSnapshot.Status.INACTIVE);
    }

    @Test
    void staleEventIsDroppedSilently() {
        // upsertLocation reports "no rows affected" when the cached
        // master_version is greater or equal — the consumer does not throw
        // and continues silently.
        when(writer.upsertLocation(any())).thenReturn(false);
        consumer.handle(buildLocationEvent("master.location.updated", "ACTIVE", 1L), "key-1");
        verify(writer, times(1)).upsertLocation(any());
    }

    @Test
    void duplicateEventSkipsApplyEntirely() {
        when(dedupe.process(any(UUID.class), any(String.class), any(Runnable.class)))
                .thenReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);
        consumer.handle(buildLocationEvent("master.location.created", "ACTIVE", 0L), "key-1");
        verify(writer, never()).upsertLocation(any());
    }

    @Test
    void malformedJsonIsRejectedAsIllegalArgument() {
        assertThatThrownBy(() -> consumer.handle("not json", "key-1"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dedupe, never()).process(any(), any(), any());
        verify(writer, never()).upsertLocation(any());
    }

    @Test
    void missingPayloadLocationIsRejected() {
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "master.location.created",
                  "occurredAt": "2026-04-25T10:00:00Z",
                  "aggregateId": "%s",
                  "aggregateType": "location",
                  "payload": {}
                }
                """.formatted(UUID.randomUUID(), LOCATION_ID);
        assertThatThrownBy(() -> consumer.handle(json, "key-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload.location");
    }

    private static String buildLocationEvent(String eventType, String status, long version) {
        UUID eventId = UUID.randomUUID();
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "eventVersion": 1,
                  "occurredAt": "2026-04-25T10:00:00Z",
                  "producer": "master-service",
                  "aggregateType": "location",
                  "aggregateId": "%s",
                  "traceId": null,
                  "actorId": null,
                  "payload": {
                    "location": {
                      "id": "%s",
                      "warehouseId": "%s",
                      "zoneId": "%s",
                      "locationCode": "WH01-A-01-01-01",
                      "aisle": "01",
                      "rack": "01",
                      "level": "01",
                      "bin": null,
                      "locationType": "STORAGE",
                      "capacityUnits": 500,
                      "status": "%s",
                      "version": %d,
                      "createdAt": "2026-04-18T00:00:00Z",
                      "createdBy": "seed-dev",
                      "updatedAt": "2026-04-25T10:00:00Z",
                      "updatedBy": "seed-dev"
                    }
                  }
                }
                """.formatted(eventId, eventType, LOCATION_ID,
                LOCATION_ID, WAREHOUSE_ID, ZONE_ID, status, version);
    }
}
