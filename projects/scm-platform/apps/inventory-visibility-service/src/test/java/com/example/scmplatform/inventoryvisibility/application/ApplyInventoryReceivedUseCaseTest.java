package com.example.scmplatform.inventoryvisibility.application;

import com.example.scmplatform.inventoryvisibility.application.port.outbound.AlertPublisherPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ClockPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ProcessedEventPort;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService.ReceivedLine;
import com.example.scmplatform.inventoryvisibility.domain.expectation.repository.InboundExpectationRepository;
import com.example.scmplatform.inventoryvisibility.domain.node.InventoryNode;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.node.repository.InventoryNodeRepository;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Quantity;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Sku;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.repository.InventorySnapshotRepository;
import com.example.scmplatform.inventoryvisibility.domain.staleness.repository.NodeStalenessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-SCM-BE-061 — {@code applyInventoryReceived} unit coverage at the application-service
 * layer, mirroring {@code ApplyInventoryConfirmedUseCaseTest}'s shape for the sibling
 * {@code applyInventoryConfirmed} use case this fix mirrors.
 *
 * <p>AC-0's consumer-level reproduction lives in
 * {@code WmsInventoryReceivedConsumerMultiLineTest} (adapter package) — this class covers
 * the service method directly: multi-line application, AC-1 redelivery idempotency, and
 * the mid-line-failure transaction-boundary edge case.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ApplyInventoryReceivedUseCaseTest {

    @Mock InventoryNodeRepository nodeRepository;
    @Mock InventorySnapshotRepository snapshotRepository;
    @Mock NodeStalenessRepository stalenessRepository;
    @Mock InboundExpectationRepository inboundExpectationRepository;
    @Mock ProcessedEventPort processedEventPort;
    @Mock AlertPublisherPort alertPublisherPort;
    @Mock ClockPort clock;

    InventoryVisibilityApplicationService service;

    private final Instant now = Instant.parse("2026-10-07T00:00:00Z");
    private final UUID eventId = UUID.randomUUID();
    private final NodeId nodeId = NodeId.of(UUID.randomUUID());
    private final String warehouseId = "WH-01";
    private final String tenantId = "scm";
    private static final String TOPIC = "wms.inventory.received.v1";

    @BeforeEach
    void setUp() {
        service = new InventoryVisibilityApplicationService(
                nodeRepository, snapshotRepository, stalenessRepository,
                inboundExpectationRepository, processedEventPort, alertPublisherPort, clock);
    }

    private InventoryNode existingNode() {
        return InventoryNode.autoRegisterWmsWarehouse(nodeId, tenantId, warehouseId, null, now);
    }

    @Test
    void duplicateEventId_isSkipped_noMutation() {
        when(processedEventPort.isDuplicate(eventId)).thenReturn(true);

        service.applyInventoryReceived(warehouseId, List.of(new ReceivedLine("SKU-1", 10L)), null,
                eventId, now, tenantId, TOPIC);

        verify(nodeRepository, never()).findByTenantIdAndExternalId(any(), any());
        verify(snapshotRepository, never()).save(any());
        verify(processedEventPort, never()).markProcessed(any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC-0/regression: a 3-line event creates 3 snapshots, dedupe checked/marked once")
    void threeLineEvent_createsThreeSnapshots() {
        when(processedEventPort.isDuplicate(eventId)).thenReturn(false);
        when(clock.now()).thenReturn(now);
        when(nodeRepository.findByTenantIdAndExternalId(tenantId, warehouseId))
                .thenReturn(Optional.of(existingNode()));
        when(snapshotRepository.findByNodeIdAndSku(any(), any(), eq(tenantId)))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(any())).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyInventoryReceived(warehouseId, List.of(
                        new ReceivedLine("SKU-A", 10L),
                        new ReceivedLine("SKU-B", 20L),
                        new ReceivedLine("SKU-C", 30L)),
                null, eventId, now, tenantId, TOPIC);

        ArgumentCaptor<InventorySnapshot> captor = ArgumentCaptor.forClass(InventorySnapshot.class);
        verify(snapshotRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues().stream().map(s -> s.getSku().value()).toList())
                .containsExactlyInAnyOrder("SKU-A", "SKU-B", "SKU-C");
        assertThat(captor.getAllValues().stream().map(s -> s.getQuantity().value()).toList())
                .containsExactlyInAnyOrder(
                        BigDecimal.valueOf(10), BigDecimal.valueOf(20), BigDecimal.valueOf(30));

        // Event-unit dedupe: checked once, marked once — not once per line.
        verify(processedEventPort, times(1)).isDuplicate(eventId);
        verify(processedEventPort, times(1)).markProcessed(eq(eventId), eq(tenantId), eq(now), eq(TOPIC));
    }

    @Test
    @DisplayName("Edge Case: the same SKU twice in one event sums (mirrors the real JPA "
            + "find-then-save flush-before-query behaviour within one transaction)")
    void sameSkuTwiceInOneEvent_sums() {
        when(processedEventPort.isDuplicate(eventId)).thenReturn(false);
        when(clock.now()).thenReturn(now);
        when(nodeRepository.findByTenantIdAndExternalId(tenantId, warehouseId))
                .thenReturn(Optional.of(existingNode()));
        when(stalenessRepository.findByNodeId(any())).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Models the real repository: findByNodeIdAndSku sees whatever the last save()
        // for this sku persisted — exactly what a JPA repository does once Hibernate
        // flushes the pending INSERT before the next SELECT in the same transaction.
        AtomicReference<InventorySnapshot> stored = new AtomicReference<>();
        when(snapshotRepository.findByNodeIdAndSku(eq(nodeId), eq(Sku.of("SKU-A")), eq(tenantId)))
                .thenAnswer(inv -> Optional.ofNullable(stored.get()));
        when(snapshotRepository.save(any())).thenAnswer(inv -> {
            InventorySnapshot s = inv.getArgument(0);
            stored.set(s);
            return s;
        });

        service.applyInventoryReceived(warehouseId, List.of(
                        new ReceivedLine("SKU-A", 10L),
                        new ReceivedLine("SKU-A", 5L)),
                null, eventId, now, tenantId, TOPIC);

        assertThat(stored.get().getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(15));
    }

    @Test
    @DisplayName("AC-1: redelivery of the same eventId is a no-op (idempotent)")
    void redelivery_sameEventId_noChange() {
        AtomicBoolean marked = new AtomicBoolean(false);
        when(processedEventPort.isDuplicate(eventId)).thenAnswer(inv -> marked.get());
        doAnswer(inv -> {
            marked.set(true);
            return null;
        }).when(processedEventPort).markProcessed(eq(eventId), any(), any(), any());
        when(clock.now()).thenReturn(now);
        when(nodeRepository.findByTenantIdAndExternalId(tenantId, warehouseId))
                .thenReturn(Optional.of(existingNode()));
        when(snapshotRepository.findByNodeIdAndSku(any(), any(), eq(tenantId)))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(any())).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ReceivedLine> lines = List.of(new ReceivedLine("SKU-A", 10L), new ReceivedLine("SKU-B", 20L));

        // First delivery: applies both lines.
        service.applyInventoryReceived(warehouseId, lines, null, eventId, now, tenantId, TOPIC);
        verify(snapshotRepository, times(2)).save(any());

        // Re-delivery of the SAME eventId: must be a pure no-op, no additional saves.
        service.applyInventoryReceived(warehouseId, lines, null, eventId, now, tenantId, TOPIC);
        verify(snapshotRepository, times(2)).save(any()); // still 2 — not 4
        verify(processedEventPort, times(1)).markProcessed(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Edge Case: a mid-line failure propagates (the @Transactional method rolls back as a unit)")
    void midLineFailure_propagatesAndNeverMarksProcessed() {
        when(processedEventPort.isDuplicate(eventId)).thenReturn(false);
        when(nodeRepository.findByTenantIdAndExternalId(tenantId, warehouseId))
                .thenReturn(Optional.of(existingNode()));
        // Line 1 (SKU-A) resolves fine; line 2 (SKU-B) blows up on lookup — simulating a
        // malformed line (Edge Case: a line with an invalid/missing skuId downstream).
        when(snapshotRepository.findByNodeIdAndSku(eq(nodeId), eq(Sku.of("SKU-A")), eq(tenantId)))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(snapshotRepository.findByNodeIdAndSku(eq(nodeId), eq(Sku.of("SKU-B")), eq(tenantId)))
                .thenThrow(new RuntimeException("simulated failure on line 2"));

        assertThatThrownBy(() -> service.applyInventoryReceived(warehouseId, List.of(
                        new ReceivedLine("SKU-A", 10L),
                        new ReceivedLine("SKU-B", 20L)),
                null, eventId, now, tenantId, TOPIC))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated failure on line 2");

        // The event must never be marked processed when a line fails — on redelivery (or
        // the @RetryableTopic retry), the whole event is retried as a unit, including the
        // line that already succeeded in this failed attempt (real rollback is covered by
        // the @Transactional boundary + the Testcontainers integration test).
        verify(processedEventPort, never()).markProcessed(any(), any(), any(), any());
    }
}
