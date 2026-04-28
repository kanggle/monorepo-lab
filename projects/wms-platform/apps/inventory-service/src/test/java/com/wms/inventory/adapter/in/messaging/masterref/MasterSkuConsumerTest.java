package com.wms.inventory.adapter.in.messaging.masterref;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.inventory.application.port.out.EventDedupePort;
import com.wms.inventory.application.port.out.MasterReadModelWriterPort;
import com.wms.inventory.domain.model.masterref.SkuSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MasterSkuConsumerTest {

    private static final UUID SKU_ID = UUID.fromString("01910000-0000-7000-8000-000000000403");
    private static final Instant FIXED_NOW = Instant.parse("2026-04-25T10:00:00Z");

    private MasterReadModelWriterPort writer;
    private EventDedupePort dedupe;
    private MasterSkuConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        writer = mock(MasterReadModelWriterPort.class);
        dedupe = mock(EventDedupePort.class);
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        consumer = new MasterSkuConsumer(new MasterEventParser(objectMapper), writer, dedupe, clock);

        doAnswer(invocation -> {
            Runnable work = invocation.getArgument(2);
            work.run();
            return EventDedupePort.Outcome.APPLIED;
        }).when(dedupe).process(any(UUID.class), any(String.class), any(Runnable.class));
    }

    @Test
    void mapsLotTrackedSkuCorrectly() {
        when(writer.upsertSku(any())).thenReturn(true);
        consumer.handle(buildSkuEvent("LOT", "ACTIVE", 0L), "key-1");

        ArgumentCaptor<SkuSnapshot> captor = ArgumentCaptor.forClass(SkuSnapshot.class);
        verify(writer).upsertSku(captor.capture());
        SkuSnapshot snapshot = captor.getValue();
        assertThat(snapshot.trackingType()).isEqualTo(SkuSnapshot.TrackingType.LOT);
        assertThat(snapshot.requiresLot()).isTrue();
        assertThat(snapshot.status()).isEqualTo(SkuSnapshot.Status.ACTIVE);
        assertThat(snapshot.cachedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void staleEventReturnsFalseFromWriter() {
        when(writer.upsertSku(any())).thenReturn(false);
        consumer.handle(buildSkuEvent("NONE", "ACTIVE", 1L), "key-1");
        verify(writer, times(1)).upsertSku(any());
    }

    private static String buildSkuEvent(String trackingType, String status, long version) {
        UUID eventId = UUID.randomUUID();
        return """
                {
                  "eventId": "%s",
                  "eventType": "master.sku.created",
                  "eventVersion": 1,
                  "occurredAt": "2026-04-25T10:00:00Z",
                  "producer": "master-service",
                  "aggregateType": "sku",
                  "aggregateId": "%s",
                  "traceId": null,
                  "actorId": null,
                  "payload": {
                    "sku": {
                      "id": "%s",
                      "skuCode": "SKU-APPLE-001",
                      "name": "Gala Apple",
                      "description": null,
                      "barcode": "8801234567890",
                      "baseUom": "EA",
                      "trackingType": "%s",
                      "weightGrams": 1000,
                      "volumeMl": null,
                      "hazardClass": null,
                      "shelfLifeDays": 30,
                      "status": "%s",
                      "version": %d,
                      "createdAt": "2026-04-18T00:00:00Z",
                      "createdBy": "seed-dev",
                      "updatedAt": "2026-04-25T10:00:00Z",
                      "updatedBy": "seed-dev"
                    }
                  }
                }
                """.formatted(eventId, SKU_ID, SKU_ID, trackingType, status, version);
    }
}
