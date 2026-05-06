package com.example.scmplatform.inventoryvisibility.domain.dedupe;

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
import com.example.scmplatform.inventoryvisibility.application.port.outbound.AlertPublisherPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies T8 idempotency: duplicate eventId must result in exactly one
 * snapshot mutation regardless of how many times the event is consumed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class EventDedupeTest {

    @Mock InventoryNodeRepository nodeRepository;
    @Mock InventorySnapshotRepository snapshotRepository;
    @Mock NodeStalenessRepository stalenessRepository;
    @Mock EventDedupePort eventDedupePort;
    @Mock AlertPublisherPort alertPublisherPort;
    @Mock ClockPort clock;

    InventoryVisibilityApplicationService service;

    private final UUID eventId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-05-01T10:00:00Z");
    private final NodeId nodeId = NodeId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new InventoryVisibilityApplicationService(
                nodeRepository, snapshotRepository, stalenessRepository,
                eventDedupePort, alertPublisherPort, clock);
    }

    @Test
    void firstProcessing_isNotDuplicate_snapshotIsSaved() {
        when(clock.now()).thenReturn(now);
        // First time: not a duplicate
        when(eventDedupePort.isDuplicate(eventId)).thenReturn(false);
        InventoryNode node = InventoryNode.autoRegisterWmsWarehouse(nodeId, "scm", "WH-001", now);
        when(nodeRepository.findByTenantIdAndExternalId("scm", "WH-001"))
                .thenReturn(Optional.of(node));
        when(snapshotRepository.findByNodeIdAndSku(eq(nodeId), any(Sku.class), eq("scm")))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(nodeId)).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyInventoryReceived("WH-001", "SKU-001", 50L, eventId, now, "scm",
                "wms.inventory.received.v1");

        verify(snapshotRepository).save(any(InventorySnapshot.class));
        verify(eventDedupePort).markProcessed(eq(eventId), eq("scm"), eq(now),
                eq("wms.inventory.received.v1"));
    }

    @Test
    void duplicateEvent_isSkipped_snapshotNotSaved() {
        // Duplicate: isDuplicate returns true
        when(eventDedupePort.isDuplicate(eventId)).thenReturn(true);

        service.applyInventoryReceived("WH-001", "SKU-001", 50L, eventId, now, "scm",
                "wms.inventory.received.v1");

        // No snapshot mutation
        verify(snapshotRepository, never()).save(any());
        verify(eventDedupePort, never()).markProcessed(any(), any(), any(), any());
    }

    @Test
    void sameEventId_processedTwice_onlyFirstMutatesSnapshot() {
        when(clock.now()).thenReturn(now);
        // First call: not duplicate
        when(eventDedupePort.isDuplicate(eventId)).thenReturn(false);
        InventoryNode node = InventoryNode.autoRegisterWmsWarehouse(nodeId, "scm", "WH-001", now);
        when(nodeRepository.findByTenantIdAndExternalId("scm", "WH-001"))
                .thenReturn(Optional.of(node));
        when(snapshotRepository.findByNodeIdAndSku(eq(nodeId), any(Sku.class), eq("scm")))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(nodeId)).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyInventoryReceived("WH-001", "SKU-001", 50L, eventId, now, "scm",
                "wms.inventory.received.v1");

        // Second call: now duplicate
        when(eventDedupePort.isDuplicate(eventId)).thenReturn(true);
        service.applyInventoryReceived("WH-001", "SKU-001", 50L, eventId, now, "scm",
                "wms.inventory.received.v1");

        // snapshotRepository.save called exactly once
        verify(snapshotRepository, org.mockito.Mockito.times(1)).save(any());
    }
}
