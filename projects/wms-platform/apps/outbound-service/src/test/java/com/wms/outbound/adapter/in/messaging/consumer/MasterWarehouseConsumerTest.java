package com.wms.outbound.adapter.in.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.MasterReadModelWriterPort;
import com.wms.outbound.domain.model.masterref.WarehouseSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MasterWarehouseConsumerTest {

    private static final UUID WAREHOUSE_ID = UUID.fromString("01910000-0000-7000-8000-000000000001");
    private static final Instant FIXED_NOW = Instant.parse("2026-04-28T10:00:00Z");

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

        // Default behaviour: dedupe runs the supplied work exactly once
        doAnswer(invocation -> {
            Runnable work = invocation.getArgument(2);
            work.run();
            return EventDedupePort.Outcome.APPLIED;
        }).when(dedupe).process(any(UUID.class), any(String.class), any(Runnable.class));
    }

    @Test
    void appliesCreatedEventThroughWriter() {
        when(writer.upsertWarehouse(any())).thenReturn(true);
        consumer.handle(buildEvent("master.warehouse.created", "ACTIVE", 0L), "key-1");

        ArgumentCaptor<WarehouseSnapshot> captor = ArgumentCaptor.forClass(WarehouseSnapshot.class);
        verify(writer).upsertWarehouse(captor.capture());
        WarehouseSnapshot snapshot = captor.getValue();
        assertThat(snapshot.id()).isEqualTo(WAREHOUSE_ID);
        assertThat(snapshot.warehouseCode()).isEqualTo("WH01");
        assertThat(snapshot.status()).isEqualTo(WarehouseSnapshot.Status.ACTIVE);
        assertThat(snapshot.cachedAt()).isEqualTo(FIXED_NOW);
        assertThat(snapshot.masterVersion()).isEqualTo(0L);
    }

    @Test
    void mapsDeactivatedToInactiveStatus() {
        when(writer.upsertWarehouse(any())).thenReturn(true);
        consumer.handle(buildEvent("master.warehouse.deactivated", "INACTIVE", 1L), "key-1");

        ArgumentCaptor<WarehouseSnapshot> captor = ArgumentCaptor.forClass(WarehouseSnapshot.class);
        verify(writer).upsertWarehouse(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(WarehouseSnapshot.Status.INACTIVE);
    }

    @Test
    void staleEventIsDroppedSilently() {
        when(writer.upsertWarehouse(any())).thenReturn(false);
        consumer.handle(buildEvent("master.warehouse.updated", "ACTIVE", 1L), "key-1");
        verify(writer, times(1)).upsertWarehouse(any());
    }

    @Test
    void duplicateEventSkipsApplyEntirely() {
        when(dedupe.process(any(UUID.class), any(String.class), any(Runnable.class)))
                .thenReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);
        consumer.handle(buildEvent("master.warehouse.created", "ACTIVE", 0L), "key-1");
        verify(writer, never()).upsertWarehouse(any());
    }

    private static String buildEvent(String eventType, String status, long version) {
        UUID eventId = UUID.randomUUID();
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "occurredAt": "2026-04-28T10:00:00Z",
                  "aggregateType": "warehouse",
                  "aggregateId": "%s",
                  "payload": {
                    "warehouse": {
                      "id": "%s",
                      "warehouseCode": "WH01",
                      "status": "%s",
                      "version": %d
                    }
                  }
                }
                """.formatted(eventId, eventType, WAREHOUSE_ID, WAREHOUSE_ID, status, version);
    }
}
