package com.wms.outbound.adapter.out.event.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.domain.event.PickingCancelledEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TASK-BE-651 — pins the producer side of {@code wms.outbound.picking.cancelled.v1}.
 *
 * <p>inventory-service could not release a reservation on a cancel because its consumer read a
 * {@code pickingRequestId} field this serializer has never written. The fix is on the consumer
 * (the producer already matches its contract, {@code outbound-events.md} §4); this test keeps the
 * producer there: the real serializer's payload field set must equal the contract block's field
 * set, {@code reservationId} must carry {@code PickingRequest.id}, and there is no
 * {@code pickingRequestId}. inventory's {@code PickingCancelledConsumerTest} asserts its wire
 * fixture against the same contract block, so serializer = contract = consumer fixture.
 */
class EventEnvelopeSerializerPickingCancelledTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path OUTBOUND_CONTRACT =
            Paths.get("specs", "contracts", "events", "outbound-events.md");

    @Test
    @DisplayName("picking.cancelled payload = exactly the outbound-events.md §4 fields; reservationId = PickingRequest.id")
    void pickingCancelledPayloadMatchesContract() throws IOException {
        UUID sagaId = UUID.randomUUID();
        UUID pickingRequestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PickingCancelledEvent event = new PickingCancelledEvent(sagaId, pickingRequestId, orderId,
                "고객 주문 취소 요청", Instant.parse("2026-10-23T13:16:00Z"), "system:test");

        JsonNode envelope = MAPPER.readTree(new EventEnvelopeSerializer(MAPPER).serialise(event).json());
        JsonNode payload = envelope.get("payload");

        Set<String> emitted = new HashSet<>();
        payload.fieldNames().forEachRemaining(emitted::add);
        Set<String> contract = contractPayloadFields();
        assertThat(contract).contains("reservationId").doesNotContain("pickingRequestId");
        assertThat(emitted).isEqualTo(contract);

        assertThat(envelope.get("eventType").asText()).isEqualTo("outbound.picking.cancelled");
        assertThat(payload.get("reservationId").asText()).isEqualTo(pickingRequestId.toString());
        assertThat(payload.get("sagaId").asText()).isEqualTo(sagaId.toString());
        assertThat(payload.get("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(payload.has("pickingRequestId")).isFalse();
    }

    /** Field names of the {@code "payload"} JSON block under {@code ### 4.} of the contract. */
    private static Set<String> contractPayloadFields() throws IOException {
        String doc = Files.readString(locate(OUTBOUND_CONTRACT), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        int heading = doc.indexOf("### 4. `outbound.picking.cancelled`");
        assertThat(heading).as("outbound-events.md § 4 heading").isGreaterThanOrEqualTo(0);
        Matcher block = Pattern.compile("```json\\n(.*?)\\n```", Pattern.DOTALL)
                .matcher(doc.substring(heading));
        assertThat(block.find()).as("outbound-events.md § 4 json block").isTrue();
        JsonNode payload = MAPPER.readTree("{" + block.group(1) + "}").get("payload");
        assertThat(payload).as("outbound-events.md § 4 block has a \"payload\" object").isNotNull();
        Set<String> fields = new HashSet<>();
        payload.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    /** Walks up from the Gradle test working directory (the module dir). */
    private static Path locate(Path relative) {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new AssertionError(relative + " not found above " + Paths.get("").toAbsolutePath()
                + " — this check must not pass without reading it");
    }
}
