package com.example.scmplatform.inventoryvisibility.adapter.inbound.messaging;

import com.example.scmplatform.inventoryvisibility.application.port.outbound.AlertPublisherPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ClockPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ProcessedEventPort;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.config.ProjectionTenant;
import com.example.scmplatform.inventoryvisibility.domain.expectation.repository.InboundExpectationRepository;
import com.example.scmplatform.inventoryvisibility.domain.node.InventoryNode;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.node.repository.InventoryNodeRepository;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.repository.InventorySnapshotRepository;
import com.example.scmplatform.inventoryvisibility.domain.staleness.repository.NodeStalenessRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-SCM-BE-061 AC-0 — reproduction + regression test.
 *
 * <p>Before the fix: {@code WmsInventoryReceivedConsumer.consume} looped over the event's
 * lines, calling the (then single-line) {@code applyInventoryReceived} once per line with
 * the SAME shared {@code eventId}. Line 1 marked that {@code eventId} processed; line 2+
 * then read as a duplicate via {@code ProcessedEventPort.isDuplicate} and were silently
 * skipped — only the first SKU of a multi-line event was ever applied.
 *
 * <p>{@link #stubStatefulDedupe} models the real {@code event_dedupe} table precisely
 * enough to reproduce this: {@code isDuplicate} returns whatever {@code markProcessed} last
 * recorded for that {@code eventId}. Run against the pre-fix code, {@code twoLineEvent_appliesBothLines}
 * fails (only 1 snapshot saved, for the first line's SKU). After the fix — dedupe is
 * checked/marked once per EVENT, mirroring the sibling {@code applyInventoryConfirmed}
 * shape — the same test passes (2 snapshots saved, one per SKU).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class WmsInventoryReceivedConsumerMultiLineTest {

    private static final String TENANT = "scm";
    private static final String WAREHOUSE_ID = "wh-multi-1";

    @Mock InventoryNodeRepository nodeRepository;
    @Mock InventorySnapshotRepository snapshotRepository;
    @Mock NodeStalenessRepository stalenessRepository;
    @Mock InboundExpectationRepository inboundExpectationRepository;
    @Mock ProcessedEventPort processedEventPort;
    @Mock AlertPublisherPort alertPublisherPort;
    @Mock ClockPort clock;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ProjectionTenant projectionTenant = new ProjectionTenant(TENANT);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final Instant now = Instant.parse("2026-10-07T00:00:00Z");
    private final NodeId nodeId = NodeId.of(UUID.randomUUID());

    private InventoryVisibilityApplicationService service;
    private WmsInventoryReceivedConsumer consumer;

    @BeforeEach
    void setUp() {
        service = new InventoryVisibilityApplicationService(
                nodeRepository, snapshotRepository, stalenessRepository,
                inboundExpectationRepository, processedEventPort, alertPublisherPort, clock);
        consumer = new WmsInventoryReceivedConsumer(service, objectMapper, projectionTenant);
    }

    /**
     * Stateful fake: {@code isDuplicate(eventId)} returns whatever
     * {@code markProcessed(eventId, ...)} last recorded for that same eventId — the real
     * {@code event_dedupe} table's observable behaviour, shared across every call the
     * consumer makes for one Kafka record.
     */
    private void stubStatefulDedupe(UUID eventId) {
        AtomicBoolean marked = new AtomicBoolean(false);
        when(processedEventPort.isDuplicate(eventId)).thenAnswer(inv -> marked.get());
        doAnswer(inv -> {
            marked.set(true);
            return null;
        }).when(processedEventPort).markProcessed(eq(eventId), any(), any(), any());
    }

    @Test
    @DisplayName("AC-0: a 2-line received event applies BOTH lines (pre-fix: only line 1)")
    void twoLineEvent_appliesBothLines() throws Exception {
        UUID eventId = UUID.randomUUID();
        stubStatefulDedupe(eventId);
        when(clock.now()).thenReturn(now);
        when(nodeRepository.findByTenantIdAndExternalId(TENANT, WAREHOUSE_ID))
                .thenReturn(Optional.of(InventoryNode.autoRegisterWmsWarehouse(
                        nodeId, TENANT, WAREHOUSE_ID, null, now)));
        when(snapshotRepository.findByNodeIdAndSku(any(), any(), eq(TENANT)))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(any())).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("warehouseId", WAREHOUSE_ID);
        payload.put("lines", List.of(
                Map.of("skuId", "SKU-A", "qtyReceived", 10),
                Map.of("skuId", "SKU-B", "qtyReceived", 20)));

        consumer.consume(
                record(eventId, "wms.inventory.received.v1", "inventory.received", payload), ack);

        ArgumentCaptor<InventorySnapshot> captor = ArgumentCaptor.forClass(InventorySnapshot.class);
        verify(snapshotRepository, times(2)).save(captor.capture());
        List<String> savedSkus = captor.getAllValues().stream()
                .map(s -> s.getSku().value())
                .toList();
        assertThat(savedSkus).containsExactlyInAnyOrder("SKU-A", "SKU-B");
    }

    private ConsumerRecord<String, String> record(UUID eventId, String topic, String eventType,
                                                    Map<String, Object> payload) throws Exception {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", eventId.toString());
        env.put("eventType", eventType);
        env.put("eventVersion", 1);
        env.put("occurredAt", now.toString());
        env.put("producer", "test");
        env.put("aggregateType", "inventory");
        env.put("aggregateId", "agg-1");
        env.put("payload", payload);
        return new ConsumerRecord<>(topic, 0, 0L, "key", objectMapper.writeValueAsString(env));
    }
}
