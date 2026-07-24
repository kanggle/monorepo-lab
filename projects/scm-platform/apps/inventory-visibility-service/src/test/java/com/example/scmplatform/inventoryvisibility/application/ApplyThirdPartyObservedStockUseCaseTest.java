package com.example.scmplatform.inventoryvisibility.application;

import com.example.scmplatform.inventoryvisibility.application.port.outbound.AlertPublisherPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ClockPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.EventDedupePort;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService.ObservedLine;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeNotFoundException;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeTypeConflictException;
import com.example.scmplatform.inventoryvisibility.domain.node.InventoryNode;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.node.repository.InventoryNodeRepository;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Quantity;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Sku;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.repository.InventorySnapshotRepository;
import com.example.scmplatform.inventoryvisibility.domain.staleness.NodeStaleness;
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
 * TASK-SCM-BE-047 — unit coverage for
 * {@link InventoryVisibilityApplicationService#applyThirdPartyObservedStock}:
 * absolute (not delta) snapshot writes for a THIRD_PARTY_LOGISTICS node, rejection
 * of an unknown/wrong-type/cross-tenant node, NodeStaleness seeding, and the
 * stale-observation ordering guard.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ApplyThirdPartyObservedStockUseCaseTest {

    private static final String TENANT = "scm";

    @Mock InventoryNodeRepository nodeRepository;
    @Mock InventorySnapshotRepository snapshotRepository;
    @Mock NodeStalenessRepository stalenessRepository;
    @Mock EventDedupePort eventDedupePort;
    @Mock AlertPublisherPort alertPublisherPort;
    @Mock ClockPort clock;

    InventoryVisibilityApplicationService service;

    private final Instant now = Instant.parse("2026-07-24T10:00:00Z");
    private final NodeId nodeId = NodeId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new InventoryVisibilityApplicationService(
                nodeRepository, snapshotRepository, stalenessRepository,
                eventDedupePort, alertPublisherPort, clock);
    }

    private InventoryNode thirdPartyNode() {
        return InventoryNode.registerThirdPartyLogistics(
                nodeId, TENANT, "3PL-EXT-1", "품고 물류센터", now);
    }

    @Test
    void newObservation_createsAbsoluteSnapshotsAndSeedsStaleness() {
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(thirdPartyNode()));
        when(snapshotRepository.findByNodeIdAndSku(eq(nodeId), any(Sku.class), eq(TENANT)))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(nodeId)).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyThirdPartyObservedStock(nodeId.toString(), TENANT, now, List.of(
                new ObservedLine("SKU-001", BigDecimal.valueOf(10)),
                new ObservedLine("SKU-002", BigDecimal.ZERO)));

        ArgumentCaptor<InventorySnapshot> snapCaptor = ArgumentCaptor.forClass(InventorySnapshot.class);
        verify(snapshotRepository, org.mockito.Mockito.times(2)).save(snapCaptor.capture());
        List<InventorySnapshot> saved = snapCaptor.getAllValues();
        assertThat(saved).anySatisfy(s -> {
            assertThat(s.getSku()).isEqualTo(Sku.of("SKU-001"));
            assertThat(s.getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(10));
        });
        assertThat(saved).anySatisfy(s -> {
            assertThat(s.getSku()).isEqualTo(Sku.of("SKU-002"));
            assertThat(s.getQuantity().value()).isEqualByComparingTo(BigDecimal.ZERO);
        });

        ArgumentCaptor<NodeStaleness> stalenessCaptor = ArgumentCaptor.forClass(NodeStaleness.class);
        verify(stalenessRepository).save(stalenessCaptor.capture());
        assertThat(stalenessCaptor.getValue().getNodeId()).isEqualTo(nodeId);
        assertThat(stalenessCaptor.getValue().getLastEventAt()).isEqualTo(now);
    }

    @Test
    void existingSnapshot_isOverwrittenAbsolutely_notAccumulated() {
        InventorySnapshot existing = InventorySnapshot.create(
                nodeId, Sku.of("SKU-001"), TENANT, Quantity.of(999), UUID.randomUUID(),
                now.minusSeconds(3600));

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(thirdPartyNode()));
        when(snapshotRepository.findByNodeIdAndSku(nodeId, Sku.of("SKU-001"), TENANT))
                .thenReturn(Optional.of(existing));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(nodeId)).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyThirdPartyObservedStock(nodeId.toString(), TENANT, now,
                List.of(new ObservedLine("SKU-001", BigDecimal.valueOf(42))));

        // Absolute set, not "999 + 42" — proves applyQuantity (not applyDelta) was used.
        assertThat(existing.getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(42));
    }

    @Test
    void olderObservedAtThanStoredSnapshot_isSkipped() {
        Instant storedLastEventAt = now;
        Instant staleObservedAt = now.minusSeconds(60);
        InventorySnapshot existing = InventorySnapshot.create(
                nodeId, Sku.of("SKU-001"), TENANT, Quantity.of(100), UUID.randomUUID(), storedLastEventAt);

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(thirdPartyNode()));
        when(snapshotRepository.findByNodeIdAndSku(nodeId, Sku.of("SKU-001"), TENANT))
                .thenReturn(Optional.of(existing));
        when(stalenessRepository.findByNodeId(nodeId)).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyThirdPartyObservedStock(nodeId.toString(), TENANT, staleObservedAt,
                List.of(new ObservedLine("SKU-001", BigDecimal.valueOf(1))));

        // Stale line skipped: snapshot quantity/lastEventAt unchanged, never saved.
        assertThat(existing.getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(existing.getLastEventAt()).isEqualTo(storedLastEventAt);
        verify(snapshotRepository, never()).save(any());
        // Staleness still refreshes — the push itself happened, even if this line was stale.
        verify(stalenessRepository).save(any());
    }

    @Test
    void zeroQuantity_isRecordedNotSkipped() {
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(thirdPartyNode()));
        when(snapshotRepository.findByNodeIdAndSku(eq(nodeId), any(Sku.class), eq(TENANT)))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(nodeId)).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyThirdPartyObservedStock(nodeId.toString(), TENANT, now,
                List.of(new ObservedLine("SKU-ZERO", BigDecimal.ZERO)));

        ArgumentCaptor<InventorySnapshot> captor = ArgumentCaptor.forClass(InventorySnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity().value()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void unknownNode_throwsNodeNotFound() {
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyThirdPartyObservedStock(
                nodeId.toString(), TENANT, now, List.of(new ObservedLine("SKU-001", BigDecimal.TEN))))
                .isInstanceOf(NodeNotFoundException.class);

        verify(snapshotRepository, never()).save(any());
        verify(stalenessRepository, never()).save(any());
    }

    @Test
    void wmsTypedNode_throwsNodeTypeConflict_neverMutatesItsSnapshot() {
        InventoryNode wmsNode = InventoryNode.autoRegisterWmsWarehouse(
                nodeId, TENANT, "WH-EXT-1", "WH01", now);
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(wmsNode));

        assertThatThrownBy(() -> service.applyThirdPartyObservedStock(
                nodeId.toString(), TENANT, now, List.of(new ObservedLine("SKU-001", BigDecimal.TEN))))
                .isInstanceOf(NodeTypeConflictException.class);

        verify(snapshotRepository, never()).findByNodeIdAndSku(any(), any(), any());
        verify(snapshotRepository, never()).save(any());
        verify(stalenessRepository, never()).save(any());
    }

    @Test
    void crossTenantNode_throwsNodeTypeConflict() {
        InventoryNode otherTenantNode = InventoryNode.registerThirdPartyLogistics(
                nodeId, "other-tenant", "3PL-EXT-1", "품고 물류센터", now);
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(otherTenantNode));

        assertThatThrownBy(() -> service.applyThirdPartyObservedStock(
                nodeId.toString(), TENANT, now, List.of(new ObservedLine("SKU-001", BigDecimal.TEN))))
                .isInstanceOf(NodeTypeConflictException.class);

        verify(snapshotRepository, never()).save(any());
        verify(stalenessRepository, never()).save(any());
    }
}
