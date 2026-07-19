package com.example.scmplatform.inventoryvisibility.application;

import com.example.scmplatform.inventoryvisibility.application.port.outbound.AlertPublisherPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ClockPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.EventDedupePort;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Acceptance Criteria #10: transferred event updates both source and destination
 * nodes. Unit test verifies the application service orchestration.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ApplyInventoryTransferredUseCaseTest {

    @Mock InventoryNodeRepository nodeRepository;
    @Mock InventorySnapshotRepository snapshotRepository;
    @Mock NodeStalenessRepository stalenessRepository;
    @Mock EventDedupePort eventDedupePort;
    @Mock AlertPublisherPort alertPublisherPort;
    @Mock ClockPort clock;

    InventoryVisibilityApplicationService service;

    private final Instant now = Instant.parse("2026-05-01T10:00:00Z");
    private final UUID eventId = UUID.randomUUID();
    private final NodeId srcNodeId = NodeId.of(UUID.randomUUID());
    private final NodeId dstNodeId = NodeId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new InventoryVisibilityApplicationService(
                nodeRepository, snapshotRepository, stalenessRepository,
                eventDedupePort, alertPublisherPort, clock);
        when(clock.now()).thenReturn(now);
        when(eventDedupePort.isDuplicate(eventId)).thenReturn(false);
    }

    @Test
    void applyTransfer_decrementsSourceAndIncrementsDestination() {
        InventoryNode srcNode = InventoryNode.autoRegisterWmsWarehouse(srcNodeId, "scm", "WH-SRC", null, now);
        InventoryNode dstNode = InventoryNode.autoRegisterWmsWarehouse(dstNodeId, "scm", "WH-DST", null, now);

        when(nodeRepository.findByTenantIdAndExternalId("scm", "WH-SRC"))
                .thenReturn(Optional.of(srcNode));
        when(nodeRepository.findByTenantIdAndExternalId("scm", "WH-DST"))
                .thenReturn(Optional.of(dstNode));

        InventorySnapshot srcSnap = InventorySnapshot.create(
                srcNodeId, Sku.of("SKU-001"), "scm", Quantity.of(100), UUID.randomUUID(), now);
        InventorySnapshot dstSnap = InventorySnapshot.create(
                dstNodeId, Sku.of("SKU-001"), "scm", Quantity.of(50), UUID.randomUUID(), now);

        when(snapshotRepository.findByNodeIdAndSku(eq(srcNodeId), any(Sku.class), eq("scm")))
                .thenReturn(Optional.of(srcSnap));
        when(snapshotRepository.findByNodeIdAndSku(eq(dstNodeId), any(Sku.class), eq("scm")))
                .thenReturn(Optional.of(dstSnap));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(any())).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyInventoryTransferred("WH-SRC", "WH-DST", "SKU-001", 30L, null,
                eventId, now, "scm", "wms.inventory.transferred.v1");

        // Source should have been decremented: 100 - 30 = 70
        assertThat(srcSnap.getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(70));
        // Destination should have been incremented: 50 + 30 = 80
        assertThat(dstSnap.getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(80));

        // Both snapshots saved
        ArgumentCaptor<InventorySnapshot> captor = ArgumentCaptor.forClass(InventorySnapshot.class);
        verify(snapshotRepository, times(2)).save(captor.capture());

        verify(eventDedupePort).markProcessed(eq(eventId), eq("scm"), eq(now), any());
    }

    @Test
    void applyTransfer_sourceNotFound_autoRegistersAndCreatesZeroSnapshot() {
        // Source node not registered — will be auto-registered
        when(nodeRepository.findByTenantIdAndExternalId("scm", "WH-NEW-SRC"))
                .thenReturn(Optional.empty());
        InventoryNode newSrcNode = InventoryNode.autoRegisterWmsWarehouse(
                NodeId.of(UUID.randomUUID()), "scm", "WH-NEW-SRC", null, now);
        when(nodeRepository.save(any())).thenReturn(newSrcNode);

        InventoryNode dstNode = InventoryNode.autoRegisterWmsWarehouse(dstNodeId, "scm", "WH-DST", null, now);
        when(nodeRepository.findByTenantIdAndExternalId("scm", "WH-DST"))
                .thenReturn(Optional.of(dstNode));

        when(snapshotRepository.findByNodeIdAndSku(any(), any(), eq("scm")))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(any())).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyInventoryTransferred("WH-NEW-SRC", "WH-DST", "SKU-001", 10L, null,
                eventId, now, "scm", "wms.inventory.transferred.v1");

        // Two snapshots created (src with ZERO base, dst with transfer quantity)
        verify(snapshotRepository, times(2)).save(any());
    }
}
