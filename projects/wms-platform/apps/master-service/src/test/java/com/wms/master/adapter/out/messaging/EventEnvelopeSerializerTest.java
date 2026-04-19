package com.wms.master.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.master.domain.event.WarehouseCreatedEvent;
import com.wms.master.domain.event.WarehouseDeactivatedEvent;
import com.wms.master.domain.event.WarehouseReactivatedEvent;
import com.wms.master.domain.event.WarehouseUpdatedEvent;
import com.wms.master.domain.model.Warehouse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class EventEnvelopeSerializerTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final EventEnvelopeSerializer serializer = new EventEnvelopeSerializer(mapper);

    @Test
    void createdEvent_producesContractEnvelope() throws Exception {
        Warehouse wh = Warehouse.create("WH01", "Seoul", "Seoul", "Asia/Seoul", "actor-42");
        WarehouseCreatedEvent event = WarehouseCreatedEvent.from(wh);

        JsonNode envelope = mapper.readTree(serializer.serialize(event));

        assertThat(envelope.get("eventType").asText()).isEqualTo("master.warehouse.created");
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("producer").asText()).isEqualTo("master-service");
        assertThat(envelope.get("aggregateType").asText()).isEqualTo("warehouse");
        assertThat(envelope.get("aggregateId").asText()).isEqualTo(wh.getId().toString());
        assertThat(envelope.get("actorId").asText()).isEqualTo("actor-42");
        assertThat(envelope.has("eventId")).isTrue();
        assertThat(envelope.has("occurredAt")).isTrue();

        JsonNode warehouse = envelope.get("payload").get("warehouse");
        assertThat(warehouse.get("warehouseCode").asText()).isEqualTo("WH01");
        assertThat(warehouse.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(warehouse.get("version").asLong()).isZero();
    }

    @Test
    void updatedEvent_carriesChangedFields() throws Exception {
        Warehouse wh = Warehouse.create("WH01", "Seoul", null, "Asia/Seoul", "actor");
        WarehouseUpdatedEvent event = WarehouseUpdatedEvent.from(wh, List.of("name", "address"));

        JsonNode envelope = mapper.readTree(serializer.serialize(event));

        JsonNode changed = envelope.get("payload").get("changedFields");
        assertThat(changed.isArray()).isTrue();
        assertThat(changed.get(0).asText()).isEqualTo("name");
        assertThat(changed.get(1).asText()).isEqualTo("address");
    }

    @Test
    void deactivatedEvent_carriesReason() throws Exception {
        Warehouse wh = Warehouse.create("WH01", "Seoul", null, "Asia/Seoul", "actor");
        wh.deactivate("actor");
        WarehouseDeactivatedEvent event = WarehouseDeactivatedEvent.from(wh, "closing");

        JsonNode envelope = mapper.readTree(serializer.serialize(event));

        assertThat(envelope.get("payload").get("reason").asText()).isEqualTo("closing");
        assertThat(envelope.get("payload").get("warehouse").get("status").asText()).isEqualTo("INACTIVE");
    }

    @Test
    void reactivatedEvent_hasSnapshotOnly() throws Exception {
        Warehouse wh = Warehouse.create("WH01", "Seoul", null, "Asia/Seoul", "actor");
        wh.deactivate("actor");
        wh.reactivate("actor");
        WarehouseReactivatedEvent event = WarehouseReactivatedEvent.from(wh);

        JsonNode envelope = mapper.readTree(serializer.serialize(event));

        assertThat(envelope.get("payload").get("warehouse").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(envelope.get("payload").has("reason")).isFalse();
        assertThat(envelope.get("payload").has("changedFields")).isFalse();
    }

    @Test
    void traceIdFromMdc_isPropagated() throws Exception {
        try {
            MDC.put("traceId", "trace-abc-123");
            Warehouse wh = Warehouse.create("WH01", "Seoul", null, "Asia/Seoul", "actor");
            JsonNode envelope = mapper.readTree(
                    serializer.serialize(WarehouseCreatedEvent.from(wh)));
            assertThat(envelope.get("traceId").asText()).isEqualTo("trace-abc-123");
        } finally {
            MDC.remove("traceId");
        }
    }

    @Test
    void missingTraceId_serializesToNull() throws Exception {
        MDC.remove("traceId");
        Warehouse wh = Warehouse.create("WH01", "Seoul", null, "Asia/Seoul", "actor");
        JsonNode envelope = mapper.readTree(
                serializer.serialize(WarehouseCreatedEvent.from(wh)));
        assertThat(envelope.get("traceId").isNull()).isTrue();
    }
}
