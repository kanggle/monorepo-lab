package com.wms.inventory.adapter.in.messaging.masterref;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.inventory.application.port.out.EventDedupePort;
import com.wms.inventory.application.port.out.MasterReadModelWriterPort;
import com.wms.inventory.domain.model.masterref.LotSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MasterLotConsumerTest {

    private static final UUID LOT_ID = UUID.fromString("01910000-0000-7000-8000-000000000601");
    private static final UUID SKU_ID = UUID.fromString("01910000-0000-7000-8000-000000000403");
    private static final Instant FIXED_NOW = Instant.parse("2026-04-25T10:00:00Z");

    private MasterReadModelWriterPort writer;
    private EventDedupePort dedupe;
    private MasterLotConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        writer = mock(MasterReadModelWriterPort.class);
        dedupe = mock(EventDedupePort.class);
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        consumer = new MasterLotConsumer(new MasterEventParser(objectMapper), writer, dedupe, clock);

        doAnswer(invocation -> {
            Runnable work = invocation.getArgument(2);
            work.run();
            return EventDedupePort.Outcome.APPLIED;
        }).when(dedupe).process(any(UUID.class), any(String.class), any(Runnable.class));
    }

    @Test
    void parsesActiveLotWithExpiry() {
        when(writer.upsertLot(any())).thenReturn(true);
        consumer.handle(buildLotEvent("ACTIVE", "2026-05-18", 0L), "key-1");

        ArgumentCaptor<LotSnapshot> captor = ArgumentCaptor.forClass(LotSnapshot.class);
        verify(writer).upsertLot(captor.capture());
        LotSnapshot snapshot = captor.getValue();
        assertThat(snapshot.id()).isEqualTo(LOT_ID);
        assertThat(snapshot.skuId()).isEqualTo(SKU_ID);
        assertThat(snapshot.status()).isEqualTo(LotSnapshot.Status.ACTIVE);
        assertThat(snapshot.expiryDate()).isEqualTo(LocalDate.of(2026, 5, 18));
    }

    @Test
    void parsesExpiredLotWithoutExpiry() {
        when(writer.upsertLot(any())).thenReturn(true);
        consumer.handle(buildLotEvent("EXPIRED", null, 5L), "key-1");

        ArgumentCaptor<LotSnapshot> captor = ArgumentCaptor.forClass(LotSnapshot.class);
        verify(writer).upsertLot(captor.capture());
        LotSnapshot snapshot = captor.getValue();
        assertThat(snapshot.status()).isEqualTo(LotSnapshot.Status.EXPIRED);
        assertThat(snapshot.expiryDate()).isNull();
    }

    private static String buildLotEvent(String status, String expiryDate, long version) {
        UUID eventId = UUID.randomUUID();
        String expiryJson = expiryDate == null ? "null" : "\"" + expiryDate + "\"";
        return """
                {
                  "eventId": "%s",
                  "eventType": "master.lot.created",
                  "eventVersion": 1,
                  "occurredAt": "2026-04-25T10:00:00Z",
                  "producer": "master-service",
                  "aggregateType": "lot",
                  "aggregateId": "%s",
                  "traceId": null,
                  "actorId": null,
                  "payload": {
                    "lot": {
                      "id": "%s",
                      "skuId": "%s",
                      "lotNo": "L-20260418-A",
                      "manufacturedDate": "2026-04-15",
                      "expiryDate": %s,
                      "supplierPartnerId": null,
                      "status": "%s",
                      "version": %d,
                      "createdAt": "2026-04-18T00:00:00Z",
                      "createdBy": "seed-dev",
                      "updatedAt": "2026-04-25T10:00:00Z",
                      "updatedBy": "seed-dev"
                    }
                  }
                }
                """.formatted(eventId, LOT_ID, LOT_ID, SKU_ID, expiryJson, status, version);
    }
}
