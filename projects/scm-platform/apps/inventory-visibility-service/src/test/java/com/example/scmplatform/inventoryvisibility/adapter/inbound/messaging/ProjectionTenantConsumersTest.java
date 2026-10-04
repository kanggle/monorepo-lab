package com.example.scmplatform.inventoryvisibility.adapter.inbound.messaging;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.batch.StalenessDetectionScheduler;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.config.ProjectionTenant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * TASK-MONO-760 — every event-driven write path, and the staleness batch that scans what
 * they wrote, uses the one configured projection tenant.
 *
 * <p>The tenant here is deliberately NOT {@code scm}: with the default, a path that still
 * hard-codes {@code "scm"} would pass. That is how the demo projection stayed empty —
 * rows landed under {@code scm} while the console read as {@code demo-corp}.
 */
@DisplayName("projection tenant reaches every event-driven write path (TASK-MONO-760)")
class ProjectionTenantConsumersTest {

    private static final String TENANT = "demo-corp";

    private final InventoryVisibilityApplicationService service = mock(InventoryVisibilityApplicationService.class);
    // Not findAndRegisterModules(): the test classpath carries jackson-module-scala, which
    // would deserialize payloads into Scala collections the consumers cannot cast.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ProjectionTenant projectionTenant = new ProjectionTenant(TENANT);
    private final Acknowledgment ack = mock(Acknowledgment.class);

    @Test
    @DisplayName("wms.inventory.received → applyInventoryReceived(…, projection tenant, …)")
    void received() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("warehouseId", "wh-1");
        payload.put("lines", List.of(Map.of("skuId", "sku-1", "qtyReceived", 3)));

        new WmsInventoryReceivedConsumer(service, objectMapper, projectionTenant)
                .consume(record("wms.inventory.received.v1", "inventory.received", payload), ack);

        verify(service).applyInventoryReceived(eq("wh-1"), eq("sku-1"), eq(3L), isNull(),
                any(UUID.class), any(Instant.class), eq(TENANT), anyString());
    }

    @Test
    @DisplayName("wms.inventory.adjusted → applyInventoryAdjusted(…, projection tenant, …)")
    void adjusted() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("locationId", "loc-1");
        payload.put("skuId", "sku-1");
        payload.put("delta", -2);

        new WmsInventoryAdjustedConsumer(service, objectMapper, projectionTenant)
                .consume(record("wms.inventory.adjusted.v1", "inventory.adjusted", payload), ack);

        verify(service).applyInventoryAdjusted(eq("loc-1"), eq("sku-1"), eq(-2L), isNull(),
                any(UUID.class), any(Instant.class), eq(TENANT), anyString());
    }

    @Test
    @DisplayName("wms.inventory.transferred → applyInventoryTransferred(…, projection tenant, …)")
    void transferred() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skuId", "sku-1");
        payload.put("quantity", 4);
        payload.put("source", Map.of("locationId", "loc-a"));
        payload.put("target", Map.of("locationId", "loc-b"));

        new WmsInventoryTransferredConsumer(service, objectMapper, projectionTenant)
                .consume(record("wms.inventory.transferred.v1", "inventory.transferred", payload), ack);

        verify(service).applyInventoryTransferred(eq("loc-a"), eq("loc-b"), eq("sku-1"), eq(4L),
                isNull(), any(UUID.class), any(Instant.class), eq(TENANT), anyString());
    }

    @Test
    @DisplayName("3PL inbound-expected → recordThirdPartyInboundExpectation(node, projection tenant, …)")
    void thirdPartyInboundExpected() throws Exception {
        String nodeId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("poId", "po-1");
        payload.put("poNumber", "PO-0001");
        payload.put("destinationNodeId", nodeId);
        payload.put("lines", List.of(Map.of("skuCode", "SKU-1", "expectedQty", "5")));

        new ScmThirdPartyInboundExpectedConsumer(service, objectMapper, projectionTenant)
                .consume(record("scm.procurement.inbound-expected.third-party.v1",
                        "scm.procurement.inbound-expected.third-party", payload), ack);

        verify(service).recordThirdPartyInboundExpectation(eq(nodeId), eq(TENANT),
                eq("po-1"), eq("PO-0001"), isNull(), any());
    }

    @Test
    @DisplayName("staleness batch scans the projection tenant, not the OAuth2 required tenant")
    void stalenessBatch() {
        new StalenessDetectionScheduler(service, projectionTenant).detectStaleNodes();

        verify(service).detectAndAlertStaleNodes(TENANT);
    }

    @Test
    @DisplayName("blank projection tenant fails at startup; surrounding whitespace is trimmed")
    void projectionTenantValue() {
        assertThatThrownBy(() -> new ProjectionTenant(" "))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new ProjectionTenant(" demo-corp ").id()).isEqualTo("demo-corp");
    }

    private ConsumerRecord<String, String> record(String topic, String eventType,
                                                  Map<String, Object> payload) throws Exception {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", UUID.randomUUID().toString());
        env.put("eventType", eventType);
        env.put("eventVersion", 1);
        env.put("occurredAt", Instant.parse("2026-10-04T00:00:00Z").toString());
        env.put("producer", "test");
        env.put("aggregateType", "inventory");
        env.put("aggregateId", "agg-1");
        env.put("payload", payload);
        return new ConsumerRecord<>(topic, 0, 0L, "key", objectMapper.writeValueAsString(env));
    }
}
