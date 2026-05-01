package com.wms.inbound.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.domain.event.AsnClosedEvent;
import com.wms.inbound.domain.event.PutawayCompletedEvent;
import com.wms.inbound.domain.event.PutawayInstructedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Asserts that the JSON envelope + payload shape produced by
 * {@link InboundEventEnvelopeSerializer} matches the contract in
 * {@code specs/contracts/events/inbound-events.md} for the three Phase-3..5
 * events introduced in TASK-BE-031.
 */
class EventContractTest {

    private final InboundEventEnvelopeSerializer serializer =
            new InboundEventEnvelopeSerializer(new ObjectMapper());
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void putawayInstructedEvent_serialisesWithExpectedShape() throws Exception {
        UUID instructionId = UUID.randomUUID();
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID putawayLineId = UUID.randomUUID();
        UUID asnLineId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID destinationLocationId = UUID.randomUUID();

        PutawayInstructedEvent event = new PutawayInstructedEvent(
                instructionId, asnId, warehouseId, "user-1",
                List.of(new PutawayInstructedEvent.Line(
                        putawayLineId, asnLineId, skuId, null,
                        destinationLocationId, "WH01-A-01-01-01", 95)),
                Instant.parse("2026-04-29T10:00:00Z"), "user-1");

        InboundEventEnvelopeSerializer.Serialised s = serializer.serialise(event);
        JsonNode root = mapper.readTree(s.json());

        assertThat(root.get("eventType").asText()).isEqualTo("inbound.putaway.instructed");
        assertThat(root.get("aggregateType").asText()).isEqualTo("putaway_instruction");
        assertThat(root.get("aggregateId").asText()).isEqualTo(instructionId.toString());
        assertThat(root.get("producer").asText()).isEqualTo("inbound-service");

        JsonNode payload = root.get("payload");
        assertThat(payload.get("putawayInstructionId").asText()).isEqualTo(instructionId.toString());
        assertThat(payload.get("asnId").asText()).isEqualTo(asnId.toString());
        assertThat(payload.get("warehouseId").asText()).isEqualTo(warehouseId.toString());
        assertThat(payload.get("plannedBy").asText()).isEqualTo("user-1");

        JsonNode line0 = payload.get("lines").get(0);
        assertThat(line0.get("putawayLineId").asText()).isEqualTo(putawayLineId.toString());
        assertThat(line0.get("asnLineId").asText()).isEqualTo(asnLineId.toString());
        assertThat(line0.get("skuId").asText()).isEqualTo(skuId.toString());
        assertThat(line0.get("lotId").isNull()).isTrue();
        assertThat(line0.get("destinationLocationId").asText()).isEqualTo(destinationLocationId.toString());
        assertThat(line0.get("destinationLocationCode").asText()).isEqualTo("WH01-A-01-01-01");
        assertThat(line0.get("qtyToPutaway").asInt()).isEqualTo(95);
    }

    @Test
    void putawayCompletedEvent_serialisesWithExpectedShape() throws Exception {
        UUID instructionId = UUID.randomUUID();
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID confirmationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        Instant completedAt = Instant.parse("2026-04-29T13:45:00Z");

        PutawayCompletedEvent event = new PutawayCompletedEvent(
                instructionId, asnId, warehouseId, completedAt,
                List.of(new PutawayCompletedEvent.Line(
                        confirmationId, skuId, null, locationId, 95)),
                completedAt, "user-1");

        InboundEventEnvelopeSerializer.Serialised s = serializer.serialise(event);
        JsonNode root = mapper.readTree(s.json());

        assertThat(root.get("eventType").asText()).isEqualTo("inbound.putaway.completed");
        assertThat(root.get("aggregateType").asText()).isEqualTo("putaway_instruction");
        assertThat(root.get("aggregateId").asText()).isEqualTo(instructionId.toString());

        JsonNode payload = root.get("payload");
        assertThat(payload.get("putawayInstructionId").asText()).isEqualTo(instructionId.toString());
        assertThat(payload.get("asnId").asText()).isEqualTo(asnId.toString());
        assertThat(payload.get("warehouseId").asText()).isEqualTo(warehouseId.toString());
        assertThat(payload.get("completedAt").asText()).isEqualTo("2026-04-29T13:45:00Z");

        JsonNode line0 = payload.get("lines").get(0);
        assertThat(line0.get("putawayConfirmationId").asText()).isEqualTo(confirmationId.toString());
        assertThat(line0.get("skuId").asText()).isEqualTo(skuId.toString());
        assertThat(line0.get("lotId").isNull()).isTrue();
        assertThat(line0.get("locationId").asText()).isEqualTo(locationId.toString());
        assertThat(line0.get("qtyReceived").asInt()).isEqualTo(95);
    }

    @Test
    void putawayCompletedEvent_emptyLines_serialisesWithEmptyArray() throws Exception {
        UUID instructionId = UUID.randomUUID();
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant completedAt = Instant.parse("2026-04-29T13:45:00Z");

        // All-skipped path — empty lines[]
        PutawayCompletedEvent event = new PutawayCompletedEvent(
                instructionId, asnId, warehouseId, completedAt,
                List.of(), completedAt, "user-1");

        InboundEventEnvelopeSerializer.Serialised s = serializer.serialise(event);
        JsonNode root = mapper.readTree(s.json());

        assertThat(root.get("payload").get("lines").isArray()).isTrue();
        assertThat(root.get("payload").get("lines")).isEmpty();
    }

    @Test
    void asnClosedEvent_serialisesWithExpectedShape() throws Exception {
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant closedAt = Instant.parse("2026-04-29T14:00:00Z");

        AsnClosedEvent event = new AsnClosedEvent(
                asnId, "ASN-20260429-0001", warehouseId, closedAt, "user-1",
                new AsnClosedEvent.Summary(100, 95, 3, 2, 95, 1),
                closedAt, "user-1");

        InboundEventEnvelopeSerializer.Serialised s = serializer.serialise(event);
        JsonNode root = mapper.readTree(s.json());

        assertThat(root.get("eventType").asText()).isEqualTo("inbound.asn.closed");
        assertThat(root.get("aggregateType").asText()).isEqualTo("asn");
        assertThat(root.get("aggregateId").asText()).isEqualTo(asnId.toString());

        JsonNode payload = root.get("payload");
        assertThat(payload.get("asnId").asText()).isEqualTo(asnId.toString());
        assertThat(payload.get("asnNo").asText()).isEqualTo("ASN-20260429-0001");
        assertThat(payload.get("warehouseId").asText()).isEqualTo(warehouseId.toString());
        assertThat(payload.get("closedAt").asText()).isEqualTo("2026-04-29T14:00:00Z");
        assertThat(payload.get("closedBy").asText()).isEqualTo("user-1");

        JsonNode summary = payload.get("summary");
        assertThat(summary.get("expectedTotal").asInt()).isEqualTo(100);
        assertThat(summary.get("passedTotal").asInt()).isEqualTo(95);
        assertThat(summary.get("damagedTotal").asInt()).isEqualTo(3);
        assertThat(summary.get("shortTotal").asInt()).isEqualTo(2);
        assertThat(summary.get("putawayConfirmedTotal").asInt()).isEqualTo(95);
        assertThat(summary.get("discrepancyCount").asInt()).isEqualTo(1);
    }
}
