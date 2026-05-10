package com.wms.inbound.adapter.in.messaging.masterref;

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
import com.wms.inbound.application.port.out.EventDedupePort;
import com.wms.inbound.application.port.out.MasterReadModelWriterPort;
import com.wms.inbound.domain.model.masterref.LocationSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link MasterEventConsumer#onLocationEvent} dedupe +
 * version-guard orchestration. The Kafka listener machinery and DB are not
 * exercised — the port contracts are mocked to verify the consumer's wiring.
 *
 * <p>The test was originally written for the per-topic
 * {@code MasterLocationConsumer}; after the dispatcher consolidation (Stage 1
 * U2) it now constructs the unified {@link MasterEventConsumer} and invokes
 * the location-specific listener method. Behaviour assertions are unchanged.
 */
class MasterLocationConsumerTest {

    private static final UUID LOCATION_ID = UUID.fromString("01910000-0000-7000-8000-000000001001");
    private static final UUID WAREHOUSE_ID = UUID.fromString("01910000-0000-7000-8000-000000000001");
    private static final UUID ZONE_ID = UUID.fromString("01910000-0000-7000-8000-000000000101");
    private static final Instant FIXED_NOW = Instant.parse("2026-04-28T10:00:00Z");

    private MasterReadModelWriterPort writer;
    private EventDedupePort dedupe;
    private MasterEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        writer = mock(MasterReadModelWriterPort.class);
        dedupe = mock(EventDedupePort.class);
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        MasterEventParser parser = new MasterEventParser(objectMapper);
        // Only the location projector is exercised here; other projectors are
        // wired in but their listener methods are not invoked.
        MasterWarehouseProjector warehouseProjector = new MasterWarehouseProjector(writer, clock);
        MasterZoneProjector zoneProjector = new MasterZoneProjector(writer, clock);
        MasterLocationProjector locationProjector = new MasterLocationProjector(writer, clock);
        MasterSkuProjector skuProjector = new MasterSkuProjector(writer, clock);
        MasterPartnerProjector partnerProjector = new MasterPartnerProjector(writer, clock);
        MasterLotProjector lotProjector = new MasterLotProjector(writer, clock);
        consumer = new MasterEventConsumer(parser, dedupe,
                warehouseProjector, zoneProjector, locationProjector,
                skuProjector, partnerProjector, lotProjector);

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
        consumer.onLocationEvent(buildLocationEvent("master.location.created", "ACTIVE", 0L));

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
        consumer.onLocationEvent(buildLocationEvent("master.location.deactivated", "INACTIVE", 1L));

        ArgumentCaptor<LocationSnapshot> captor = ArgumentCaptor.forClass(LocationSnapshot.class);
        verify(writer).upsertLocation(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(LocationSnapshot.Status.INACTIVE);
    }

    @Test
    void staleEventIsDroppedSilently() {
        when(writer.upsertLocation(any())).thenReturn(false);
        consumer.onLocationEvent(buildLocationEvent("master.location.updated", "ACTIVE", 1L));
        verify(writer, times(1)).upsertLocation(any());
    }

    @Test
    void duplicateEventSkipsApplyEntirely() {
        when(dedupe.process(any(UUID.class), any(String.class), any(Runnable.class)))
                .thenReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);
        consumer.onLocationEvent(buildLocationEvent("master.location.created", "ACTIVE", 0L));
        verify(writer, never()).upsertLocation(any());
    }

    @Test
    void malformedJsonIsRejectedAsIllegalArgument() {
        assertThatThrownBy(() -> consumer.onLocationEvent("not json"))
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
                  "occurredAt": "2026-04-28T10:00:00Z",
                  "aggregateId": "%s",
                  "aggregateType": "location",
                  "payload": {}
                }
                """.formatted(UUID.randomUUID(), LOCATION_ID);
        assertThatThrownBy(() -> consumer.onLocationEvent(json))
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
                  "occurredAt": "2026-04-28T10:00:00Z",
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
                      "updatedAt": "2026-04-28T10:00:00Z",
                      "updatedBy": "seed-dev"
                    }
                  }
                }
                """.formatted(eventId, eventType, LOCATION_ID,
                LOCATION_ID, WAREHOUSE_ID, ZONE_ID, status, version);
    }
}
