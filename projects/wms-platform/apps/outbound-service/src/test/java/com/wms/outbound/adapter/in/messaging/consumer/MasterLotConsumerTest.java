package com.wms.outbound.adapter.in.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.MasterReadModelWriterPort;
import com.wms.outbound.domain.model.masterref.LotSnapshot;
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
    private static final Instant FIXED_NOW = Instant.parse("2026-04-28T10:00:00Z");

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
    void mapsExpiredEventToExpiredStatus() {
        when(writer.upsertLot(any())).thenReturn(true);
        consumer.handle(buildEvent("master.lot.expired", "EXPIRED", 2L), "key-1");

        ArgumentCaptor<LotSnapshot> captor = ArgumentCaptor.forClass(LotSnapshot.class);
        verify(writer).upsertLot(captor.capture());
        LotSnapshot snapshot = captor.getValue();
        assertThat(snapshot.status()).isEqualTo(LotSnapshot.Status.EXPIRED);
        assertThat(snapshot.id()).isEqualTo(LOT_ID);
        assertThat(snapshot.skuId()).isEqualTo(SKU_ID);
        assertThat(snapshot.expiryDate()).isEqualTo(LocalDate.parse("2026-05-18"));
    }

    @Test
    void appliesCreatedEventWithActiveStatus() {
        when(writer.upsertLot(any())).thenReturn(true);
        consumer.handle(buildEvent("master.lot.created", "ACTIVE", 0L), "key-1");

        ArgumentCaptor<LotSnapshot> captor = ArgumentCaptor.forClass(LotSnapshot.class);
        verify(writer).upsertLot(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(LotSnapshot.Status.ACTIVE);
    }

    private static String buildEvent(String eventType, String status, long version) {
        UUID eventId = UUID.randomUUID();
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "occurredAt": "2026-04-28T10:00:00Z",
                  "aggregateType": "lot",
                  "aggregateId": "%s",
                  "payload": {
                    "lot": {
                      "id": "%s",
                      "skuId": "%s",
                      "lotNo": "L-20260418-A",
                      "expiryDate": "2026-05-18",
                      "status": "%s",
                      "version": %d
                    }
                  }
                }
                """.formatted(eventId, eventType, LOT_ID, LOT_ID, SKU_ID, status, version);
    }
}
