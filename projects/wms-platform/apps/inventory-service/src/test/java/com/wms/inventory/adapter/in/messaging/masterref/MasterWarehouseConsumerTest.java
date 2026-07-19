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
import com.wms.inventory.domain.model.masterref.WarehouseSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link MasterWarehouseConsumer} dedupe + version-guard orchestration
 * (ADR-MONO-050 D9). The Kafka listener machinery and DB are not exercised — ports are mocked.
 */
class MasterWarehouseConsumerTest {

    private static final UUID WAREHOUSE_ID = UUID.fromString("01910000-0000-7000-8000-000000000001");
    private static final Instant FIXED_NOW = Instant.parse("2026-04-25T10:00:00Z");

    private MasterReadModelWriterPort writer;
    private EventDedupePort dedupe;
    private MasterWarehouseConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        writer = mock(MasterReadModelWriterPort.class);
        dedupe = mock(EventDedupePort.class);
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        consumer = new MasterWarehouseConsumer(new MasterEventParser(objectMapper), writer, dedupe, clock);

        doAnswer(invocation -> {
            Runnable work = invocation.getArgument(2);
            work.run();
            return EventDedupePort.Outcome.APPLIED;
        }).when(dedupe).process(any(UUID.class), any(String.class), any(Runnable.class));
    }

    @Test
    void appliesCreatedEventThroughWriter() {
        when(writer.upsertWarehouse(any())).thenReturn(true);
        consumer.handle(buildWarehouseEvent("master.warehouse.created", "WH-SEOUL-01", "ACTIVE", 0L), "key-1");

        ArgumentCaptor<WarehouseSnapshot> captor = ArgumentCaptor.forClass(WarehouseSnapshot.class);
        verify(writer).upsertWarehouse(captor.capture());
        WarehouseSnapshot snapshot = captor.getValue();
        assertThat(snapshot.id()).isEqualTo(WAREHOUSE_ID);
        assertThat(snapshot.warehouseCode()).isEqualTo("WH-SEOUL-01");
        assertThat(snapshot.status()).isEqualTo(WarehouseSnapshot.Status.ACTIVE);
        assertThat(snapshot.cachedAt()).isEqualTo(FIXED_NOW);
        assertThat(snapshot.masterVersion()).isEqualTo(0L);
    }

    @Test
    void mapsDeactivatedToInactiveStatus() {
        when(writer.upsertWarehouse(any())).thenReturn(true);
        consumer.handle(buildWarehouseEvent("master.warehouse.deactivated", "WH-SEOUL-01", "INACTIVE", 1L), "key-1");

        ArgumentCaptor<WarehouseSnapshot> captor = ArgumentCaptor.forClass(WarehouseSnapshot.class);
        verify(writer).upsertWarehouse(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(WarehouseSnapshot.Status.INACTIVE);
    }

    @Test
    void staleEventIsDroppedSilently() {
        when(writer.upsertWarehouse(any())).thenReturn(false);
        consumer.handle(buildWarehouseEvent("master.warehouse.updated", "WH-SEOUL-01", "ACTIVE", 1L), "key-1");
        verify(writer, times(1)).upsertWarehouse(any());
    }

    @Test
    void duplicateEventSkipsApplyEntirely() {
        when(dedupe.process(any(UUID.class), any(String.class), any(Runnable.class)))
                .thenReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);
        consumer.handle(buildWarehouseEvent("master.warehouse.created", "WH-SEOUL-01", "ACTIVE", 0L), "key-1");
        verify(writer, never()).upsertWarehouse(any());
    }

    @Test
    void malformedJsonIsRejectedAsIllegalArgument() {
        assertThatThrownBy(() -> consumer.handle("not json", "key-1"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dedupe, never()).process(any(), any(), any());
        verify(writer, never()).upsertWarehouse(any());
    }

    @Test
    void missingPayloadWarehouseIsRejected() {
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "master.warehouse.created",
                  "occurredAt": "2026-04-25T10:00:00Z",
                  "aggregateId": "%s",
                  "aggregateType": "warehouse",
                  "payload": {}
                }
                """.formatted(UUID.randomUUID(), WAREHOUSE_ID);
        assertThatThrownBy(() -> consumer.handle(json, "key-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload.warehouse");
    }

    private static String buildWarehouseEvent(String eventType, String warehouseCode,
                                              String status, long version) {
        UUID eventId = UUID.randomUUID();
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "eventVersion": 1,
                  "occurredAt": "2026-04-25T10:00:00Z",
                  "producer": "master-service",
                  "aggregateType": "warehouse",
                  "aggregateId": "%s",
                  "traceId": null,
                  "actorId": null,
                  "payload": {
                    "warehouse": {
                      "id": "%s",
                      "warehouseCode": "%s",
                      "name": "Seoul Main",
                      "address": "Seoul, Korea",
                      "timezone": "Asia/Seoul",
                      "status": "%s",
                      "version": %d,
                      "createdAt": "2026-04-18T00:00:00Z",
                      "createdBy": "seed-dev",
                      "updatedAt": "2026-04-25T10:00:00Z",
                      "updatedBy": "seed-dev"
                    }
                  }
                }
                """.formatted(eventId, eventType, WAREHOUSE_ID,
                WAREHOUSE_ID, warehouseCode, status, version);
    }
}
