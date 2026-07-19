package com.example.scmplatform.inventoryvisibility.application;

import com.example.scmplatform.inventoryvisibility.application.port.outbound.AlertPublisherPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ClockPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.EventDedupePort;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.domain.node.InventoryNode;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.node.repository.InventoryNodeRepository;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-MONO-050 D9 / TASK-SCM-BE-037 — the node read-model learns the wms warehouse CODE
 * from inventory mutation events, so demand-planning's batch sweep can address a
 * replenishment PO by code instead of uuid.
 *
 * <p>Covers the application-service orchestration of the set-if-present rule:
 * an incoming non-null code is persisted; an incoming null is ignored (never wipes a
 * previously stored code, and does not trigger a redundant write).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ApplyWarehouseCodeUseCaseTest {

    private static final String TENANT = "scm";
    private static final String EXTERNAL_ID = "WH-EXT-1";
    private static final String TOPIC = "wms.inventory.received.v1";

    @Mock InventoryNodeRepository nodeRepository;
    @Mock InventorySnapshotRepository snapshotRepository;
    @Mock NodeStalenessRepository stalenessRepository;
    @Mock EventDedupePort eventDedupePort;
    @Mock AlertPublisherPort alertPublisherPort;
    @Mock ClockPort clock;

    InventoryVisibilityApplicationService service;

    private final Instant now = Instant.parse("2026-06-01T10:00:00Z");
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InventoryVisibilityApplicationService(
                nodeRepository, snapshotRepository, stalenessRepository,
                eventDedupePort, alertPublisherPort, clock);
        when(clock.now()).thenReturn(now);
        when(eventDedupePort.isDuplicate(eventId)).thenReturn(false);
        when(snapshotRepository.findByNodeIdAndSku(any(), any(), eq(TENANT)))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(any())).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private InventoryNode existingNode(String storedWarehouseCode) {
        return InventoryNode.autoRegisterWmsWarehouse(
                NodeId.of(UUID.randomUUID()), TENANT, EXTERNAL_ID, storedWarehouseCode, now);
    }

    private InventoryNode savedNode() {
        ArgumentCaptor<InventoryNode> captor = ArgumentCaptor.forClass(InventoryNode.class);
        verify(nodeRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void autoRegisteredNodeCarriesTheIncomingWarehouseCode() {
        when(nodeRepository.findByTenantIdAndExternalId(TENANT, EXTERNAL_ID))
                .thenReturn(Optional.empty());
        when(nodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyInventoryReceived(EXTERNAL_ID, "SKU-001", 50L, "WH01",
                eventId, now, TENANT, TOPIC);

        assertThat(savedNode().getWarehouseCode()).isEqualTo("WH01");
    }

    @Test
    void existingNodeLearnsTheCodeOnFirstNonNullEvent() {
        when(nodeRepository.findByTenantIdAndExternalId(TENANT, EXTERNAL_ID))
                .thenReturn(Optional.of(existingNode(null)));
        when(nodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyInventoryReceived(EXTERNAL_ID, "SKU-001", 50L, "WH01",
                eventId, now, TENANT, TOPIC);

        assertThat(savedNode().getWarehouseCode()).isEqualTo("WH01");
    }

    /**
     * The load-bearing rule: wms emits the code best-effort, so a later null-code event
     * must leave the stored code intact — and must not even write.
     */
    @Test
    void laterNullCodeEventDoesNotWipeTheStoredCode() {
        InventoryNode stored = existingNode("WH01");
        when(nodeRepository.findByTenantIdAndExternalId(TENANT, EXTERNAL_ID))
                .thenReturn(Optional.of(stored));

        service.applyInventoryReceived(EXTERNAL_ID, "SKU-001", 50L, null,
                eventId, now, TENANT, TOPIC);

        assertThat(stored.getWarehouseCode()).isEqualTo("WH01");
        verify(nodeRepository, never()).save(any());
    }

    /** A null code must never block the projection itself — the snapshot still applies. */
    @Test
    void nullCodeStillProjectsTheSnapshot() {
        when(nodeRepository.findByTenantIdAndExternalId(TENANT, EXTERNAL_ID))
                .thenReturn(Optional.of(existingNode(null)));

        service.applyInventoryReceived(EXTERNAL_ID, "SKU-001", 50L, null,
                eventId, now, TENANT, TOPIC);

        verify(snapshotRepository).save(any());
        verify(eventDedupePort).markProcessed(eq(eventId), eq(TENANT), eq(now), eq(TOPIC));
    }

    @Test
    void adjustedEventAlsoCarriesTheCodeOntoTheNode() {
        when(nodeRepository.findByTenantIdAndExternalId(TENANT, EXTERNAL_ID))
                .thenReturn(Optional.of(existingNode(null)));
        when(nodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyInventoryAdjusted(EXTERNAL_ID, "SKU-001", -5L, "WH02",
                eventId, now, TENANT, "wms.inventory.adjusted.v1");

        assertThat(savedNode().getWarehouseCode()).isEqualTo("WH02");
    }
}
