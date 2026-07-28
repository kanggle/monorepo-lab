package com.example.scmplatform.inventoryvisibility.application;

import com.example.scmplatform.inventoryvisibility.application.port.outbound.AlertPublisherPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ClockPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.EventDedupePort;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService.ExpectedLine;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService.ObservedLine;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeNotFoundException;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeTypeConflictException;
import com.example.scmplatform.inventoryvisibility.domain.expectation.ExpectationStatus;
import com.example.scmplatform.inventoryvisibility.domain.expectation.InboundExpectation;
import com.example.scmplatform.inventoryvisibility.domain.expectation.repository.InboundExpectationRepository;
import com.example.scmplatform.inventoryvisibility.domain.node.InventoryNode;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.node.repository.InventoryNodeRepository;
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
import java.time.LocalDate;
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
 * TASK-SCM-BE-049 — unit coverage for the scm-internal 3PL inbound-expectation
 * sink: recording an expectation against a THIRD_PARTY_LOGISTICS node, fail-closed
 * rejection of an absent/wrong-type/cross-tenant node, idempotency on the PO
 * reference, and observation-driven reconciliation (binary satisfy) via
 * {@link InventoryVisibilityApplicationService#applyThirdPartyObservedStock}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ThirdPartyInboundExpectationSinkUseCaseTest {

    private static final String TENANT = "scm";

    @Mock InventoryNodeRepository nodeRepository;
    @Mock InventorySnapshotRepository snapshotRepository;
    @Mock NodeStalenessRepository stalenessRepository;
    @Mock InboundExpectationRepository inboundExpectationRepository;
    @Mock EventDedupePort eventDedupePort;
    @Mock AlertPublisherPort alertPublisherPort;
    @Mock ClockPort clock;

    InventoryVisibilityApplicationService service;

    private final Instant now = Instant.parse("2026-07-28T10:00:00Z");
    private final NodeId nodeId = NodeId.of(UUID.randomUUID());
    private final LocalDate expectedAt = LocalDate.parse("2026-08-04");

    @BeforeEach
    void setUp() {
        service = new InventoryVisibilityApplicationService(
                nodeRepository, snapshotRepository, stalenessRepository,
                inboundExpectationRepository, eventDedupePort, alertPublisherPort, clock);
    }

    private InventoryNode thirdPartyNode() {
        return InventoryNode.registerThirdPartyLogistics(nodeId, TENANT, "3PL-EXT-1", "품고 물류센터", now);
    }

    // ---------------- record ----------------

    @Test
    void record_newExpectation_persistsOpenRowPerLine() {
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(thirdPartyNode()));
        when(clock.now()).thenReturn(now);
        when(inboundExpectationRepository.exists(eq(TENANT), eq("PO-1"), any(Sku.class), eq(nodeId)))
                .thenReturn(false);
        when(inboundExpectationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordThirdPartyInboundExpectation(nodeId.toString(), TENANT, "po-1", "PO-1", expectedAt,
                List.of(new ExpectedLine("SKU-001", new BigDecimal("100"))));

        ArgumentCaptor<InboundExpectation> captor = ArgumentCaptor.forClass(InboundExpectation.class);
        verify(inboundExpectationRepository).save(captor.capture());
        InboundExpectation saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ExpectationStatus.OPEN);
        assertThat(saved.getNodeId()).isEqualTo(nodeId);
        assertThat(saved.getSku()).isEqualTo(Sku.of("SKU-001"));
        assertThat(saved.getExpectedQuantity().value()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(saved.getSourcePoNumber()).isEqualTo("PO-1");
        assertThat(saved.getExpectedAt()).isEqualTo(expectedAt);
    }

    @Test
    void record_duplicatePoLine_isIdempotentSkip() {
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(thirdPartyNode()));
        when(clock.now()).thenReturn(now);
        when(inboundExpectationRepository.exists(TENANT, "PO-1", Sku.of("SKU-001"), nodeId))
                .thenReturn(true);

        service.recordThirdPartyInboundExpectation(nodeId.toString(), TENANT, "po-1", "PO-1", expectedAt,
                List.of(new ExpectedLine("SKU-001", new BigDecimal("100"))));

        verify(inboundExpectationRepository, never()).save(any());
    }

    @Test
    void record_unknownNode_throwsNodeNotFound_andRecordsNothing() {
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordThirdPartyInboundExpectation(
                nodeId.toString(), TENANT, "po-1", "PO-1", expectedAt,
                List.of(new ExpectedLine("SKU-001", new BigDecimal("100")))))
                .isInstanceOf(NodeNotFoundException.class);

        verify(inboundExpectationRepository, never()).save(any());
    }

    @Test
    void record_wmsNode_throwsNodeTypeConflict() {
        InventoryNode wmsNode = InventoryNode.autoRegisterWmsWarehouse(nodeId, TENANT, "WH-1", "WH01", now);
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(wmsNode));

        assertThatThrownBy(() -> service.recordThirdPartyInboundExpectation(
                nodeId.toString(), TENANT, "po-1", "PO-1", expectedAt,
                List.of(new ExpectedLine("SKU-001", new BigDecimal("100")))))
                .isInstanceOf(NodeTypeConflictException.class);

        verify(inboundExpectationRepository, never()).save(any());
    }

    @Test
    void record_crossTenantNode_throwsNodeTypeConflict() {
        InventoryNode otherTenant = InventoryNode.registerThirdPartyLogistics(
                nodeId, "other-tenant", "3PL-EXT-1", "품고 물류센터", now);
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(otherTenant));

        assertThatThrownBy(() -> service.recordThirdPartyInboundExpectation(
                nodeId.toString(), TENANT, "po-1", "PO-1", expectedAt,
                List.of(new ExpectedLine("SKU-001", new BigDecimal("100")))))
                .isInstanceOf(NodeTypeConflictException.class);

        verify(inboundExpectationRepository, never()).save(any());
    }

    // ---------------- reconciliation (via observation) ----------------

    @Test
    void observation_meetingExpectedQty_marksExpectationSatisfied() {
        InboundExpectation open = InboundExpectation.record(
                TENANT, nodeId, Sku.of("SKU-001"), Quantity.of(100),
                "po-1", "PO-1", expectedAt, now.minusSeconds(3600));

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(thirdPartyNode()));
        when(snapshotRepository.findByNodeIdAndSku(eq(nodeId), any(Sku.class), eq(TENANT)))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(nodeId)).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inboundExpectationRepository.findOpenByNodeAndSku(nodeId, Sku.of("SKU-001"), TENANT))
                .thenReturn(List.of(open));
        when(inboundExpectationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clock.now()).thenReturn(now);

        service.applyThirdPartyObservedStock(nodeId.toString(), TENANT, now,
                List.of(new ObservedLine("SKU-001", new BigDecimal("120"))));

        ArgumentCaptor<InboundExpectation> captor = ArgumentCaptor.forClass(InboundExpectation.class);
        verify(inboundExpectationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ExpectationStatus.SATISFIED);
        assertThat(captor.getValue().getSatisfiedAt()).isEqualTo(now);
    }

    @Test
    void observation_belowExpectedQty_leavesExpectationOpen() {
        InboundExpectation open = InboundExpectation.record(
                TENANT, nodeId, Sku.of("SKU-001"), Quantity.of(100),
                "po-1", "PO-1", expectedAt, now.minusSeconds(3600));

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(thirdPartyNode()));
        when(snapshotRepository.findByNodeIdAndSku(eq(nodeId), any(Sku.class), eq(TENANT)))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stalenessRepository.findByNodeId(nodeId)).thenReturn(Optional.empty());
        when(stalenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inboundExpectationRepository.findOpenByNodeAndSku(nodeId, Sku.of("SKU-001"), TENANT))
                .thenReturn(List.of(open));
        when(clock.now()).thenReturn(now);

        service.applyThirdPartyObservedStock(nodeId.toString(), TENANT, now,
                List.of(new ObservedLine("SKU-001", new BigDecimal("40"))));

        // Binary rule: 40 < 100 → expectation stays OPEN, never saved as satisfied.
        assertThat(open.getStatus()).isEqualTo(ExpectationStatus.OPEN);
        verify(inboundExpectationRepository, never()).save(any());
    }
}
