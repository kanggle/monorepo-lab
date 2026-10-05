package com.example.scmplatform.inventoryvisibility.application;

import com.example.scmplatform.inventoryvisibility.application.port.outbound.AlertPublisherPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ClockPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ProcessedEventPort;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService.ConfirmedLine;
import com.example.scmplatform.inventoryvisibility.domain.error.InventorySnapshotNotFoundException;
import com.example.scmplatform.inventoryvisibility.domain.error.NegativeSnapshotQuantityException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-762 AC-0 ⓐ / AC-1 / AC-3 — {@code applyInventoryConfirmed} unit coverage.
 * On-hand decrement only; no auto-create; retry-eligible (unchecked exception) on a
 * missing row or a would-go-negative decrement.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ApplyInventoryConfirmedUseCaseTest {

    @Mock InventoryNodeRepository nodeRepository;
    @Mock InventorySnapshotRepository snapshotRepository;
    @Mock NodeStalenessRepository stalenessRepository;
    @Mock InboundExpectationRepository inboundExpectationRepository;
    @Mock ProcessedEventPort processedEventPort;
    @Mock AlertPublisherPort alertPublisherPort;
    @Mock ClockPort clock;

    InventoryVisibilityApplicationService service;

    private final Instant now = Instant.parse("2026-10-05T06:47:28Z");
    private final UUID eventId = UUID.randomUUID();
    private final NodeId nodeId = NodeId.of(UUID.randomUUID());
    private final String warehouseId = "WH-01";
    private final String tenantId = "scm";

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

        service.applyInventoryConfirmed(warehouseId, List.of(new ConfirmedLine("SKU-1", 10L)),
                eventId, now, tenantId, "wms.inventory.confirmed.v1");

        verify(nodeRepository, never()).findByTenantIdAndExternalId(any(), any());
        verify(snapshotRepository, never()).save(any());
        verify(processedEventPort, never()).markProcessed(any(), any(), any(), any());
    }

    @Test
    void singleLine_decrementsOnHand_95minus10equals85() {
        when(processedEventPort.isDuplicate(eventId)).thenReturn(false);
        when(clock.now()).thenReturn(now);
        when(nodeRepository.findByTenantIdAndExternalId(tenantId, warehouseId))
                .thenReturn(Optional.of(existingNode()));
        InventorySnapshot snapshot = InventorySnapshot.create(
                nodeId, Sku.of("SKU-1"), tenantId, Quantity.of(95), UUID.randomUUID(), now);
        when(snapshotRepository.findByNodeIdAndSku(eq(nodeId), any(Sku.class), eq(tenantId)))
                .thenReturn(Optional.of(snapshot));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(any())).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyInventoryConfirmed(warehouseId, List.of(new ConfirmedLine("SKU-1", 10L)),
                eventId, now, tenantId, "wms.inventory.confirmed.v1");

        assertThat(snapshot.getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(85));
        verify(snapshotRepository).save(snapshot);
        verify(processedEventPort).markProcessed(eq(eventId), eq(tenantId), eq(now), any());
    }

    @Test
    void multiLine_decrementsEachLineIndependently() {
        when(processedEventPort.isDuplicate(eventId)).thenReturn(false);
        when(clock.now()).thenReturn(now);
        when(nodeRepository.findByTenantIdAndExternalId(tenantId, warehouseId))
                .thenReturn(Optional.of(existingNode()));

        InventorySnapshot snapA = InventorySnapshot.create(
                nodeId, Sku.of("SKU-A"), tenantId, Quantity.of(50), UUID.randomUUID(), now);
        InventorySnapshot snapB = InventorySnapshot.create(
                nodeId, Sku.of("SKU-B"), tenantId, Quantity.of(30), UUID.randomUUID(), now);
        when(snapshotRepository.findByNodeIdAndSku(eq(nodeId), eq(Sku.of("SKU-A")), eq(tenantId)))
                .thenReturn(Optional.of(snapA));
        when(snapshotRepository.findByNodeIdAndSku(eq(nodeId), eq(Sku.of("SKU-B")), eq(tenantId)))
                .thenReturn(Optional.of(snapB));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(any())).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyInventoryConfirmed(warehouseId,
                List.of(new ConfirmedLine("SKU-A", 5L), new ConfirmedLine("SKU-B", 3L)),
                eventId, now, tenantId, "wms.inventory.confirmed.v1");

        assertThat(snapA.getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(45));
        assertThat(snapB.getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(27));
    }

    @Test
    void missingNode_throwsInventorySnapshotNotFoundException_noRowCreated() {
        when(processedEventPort.isDuplicate(eventId)).thenReturn(false);
        when(nodeRepository.findByTenantIdAndExternalId(tenantId, warehouseId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyInventoryConfirmed(warehouseId,
                List.of(new ConfirmedLine("SKU-1", 10L)),
                eventId, now, tenantId, "wms.inventory.confirmed.v1"))
                .isInstanceOf(InventorySnapshotNotFoundException.class);

        verify(snapshotRepository, never()).save(any());
        verify(processedEventPort, never()).markProcessed(any(), any(), any(), any());
    }

    @Test
    void missingSnapshotRowForSku_throwsInventorySnapshotNotFoundException_noRowCreated() {
        when(processedEventPort.isDuplicate(eventId)).thenReturn(false);
        when(nodeRepository.findByTenantIdAndExternalId(tenantId, warehouseId))
                .thenReturn(Optional.of(existingNode()));
        when(snapshotRepository.findByNodeIdAndSku(eq(nodeId), any(Sku.class), eq(tenantId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyInventoryConfirmed(warehouseId,
                List.of(new ConfirmedLine("SKU-1", 10L)),
                eventId, now, tenantId, "wms.inventory.confirmed.v1"))
                .isInstanceOf(InventorySnapshotNotFoundException.class);

        verify(snapshotRepository, never()).save(any());
        verify(processedEventPort, never()).markProcessed(any(), any(), any(), any());
    }

    @Test
    void decrementExceedsStoredQuantity_throwsNegativeSnapshotQuantityException_notClamped() {
        when(processedEventPort.isDuplicate(eventId)).thenReturn(false);
        when(nodeRepository.findByTenantIdAndExternalId(tenantId, warehouseId))
                .thenReturn(Optional.of(existingNode()));
        InventorySnapshot snapshot = InventorySnapshot.create(
                nodeId, Sku.of("SKU-1"), tenantId, Quantity.of(5), UUID.randomUUID(), now);
        when(snapshotRepository.findByNodeIdAndSku(eq(nodeId), any(Sku.class), eq(tenantId)))
                .thenReturn(Optional.of(snapshot));

        assertThatThrownBy(() -> service.applyInventoryConfirmed(warehouseId,
                List.of(new ConfirmedLine("SKU-1", 10L)),
                eventId, now, tenantId, "wms.inventory.confirmed.v1"))
                .isInstanceOf(NegativeSnapshotQuantityException.class);

        // Not clamped to zero — the in-memory quantity must be untouched by the failed attempt.
        assertThat(snapshot.getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(5));
        verify(snapshotRepository, never()).save(any());
        verify(processedEventPort, never()).markProcessed(any(), any(), any(), any());
    }
}
