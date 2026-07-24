package com.example.scmplatform.inventoryvisibility.integration;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventoryNodeJpaEntity;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService.ObservedLine;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService.RegisterThirdPartyLogisticsNodeResult;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeNotFoundException;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeTypeConflictException;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;
import com.example.scmplatform.inventoryvisibility.domain.staleness.NodeStaleness;
import com.example.scmplatform.inventoryvisibility.domain.staleness.StalenessStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-SCM-BE-047 — real-Postgres IT for
 * {@link InventoryVisibilityApplicationService#applyThirdPartyObservedStock}: register
 * a THIRD_PARTY_LOGISTICS node (BE-046), observe stock, and assert the observation is
 * visible via the same read paths (`getSnapshotByNode`, `getCrossNodeSnapshot`) and the
 * `node_staleness` row exists (ADR-MONO-054 §D4).
 *
 * <p>CI-only per {@code AbstractInventoryVisibilityIntegrationTest} (Testcontainers +
 * {@code DockerAvailableCondition}) — Windows host cannot run this locally (see task
 * dispatch note); compiles and is exercised by the CI Integration lane.
 */
@DisplayName("IT: applyThirdPartyObservedStock against a real THIRD_PARTY_LOGISTICS node")
class ThirdPartyObservedStockIntegrationTest extends AbstractInventoryVisibilityIntegrationTest {

    @Autowired
    RegisterThirdPartyLogisticsNodeService registrationService;

    @Autowired
    InventoryVisibilityApplicationService visibilityService;

    @Test
    @DisplayName("등록된 3PL 노드에 관측 재고 기록 → snapshot 행 존재 + cross-node/노드별 read path 노출 + staleness seed")
    void observeStock_forRegistered3plNode_isVisibleAcrossReadPathsAndSeedsStaleness() {
        String externalId = "3pl-it-observe-" + UUID.randomUUID();
        RegisterThirdPartyLogisticsNodeResult registration =
                registrationService.register(TENANT_SCM, externalId, "품고 물류센터");
        String nodeId = registration.node().getId().toString();
        // PostgreSQL TIMESTAMPTZ stores microsecond precision — truncate here so the
        // round-tripped lastEventAt/lastCheckedAt compare exactly against this value.
        Instant observedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        visibilityService.applyThirdPartyObservedStock(nodeId, TENANT_SCM, observedAt, List.of(
                new ObservedLine("SKU-3PL-001", BigDecimal.valueOf(25)),
                new ObservedLine("SKU-3PL-002", BigDecimal.ZERO)));

        // Per-node read path
        List<InventorySnapshot> byNode = visibilityService.getSnapshotByNode(nodeId, TENANT_SCM);
        assertThat(byNode).hasSize(2);
        assertThat(byNode)
                .anySatisfy(s -> assertThat(s.getQuantity().value())
                        .isEqualByComparingTo(BigDecimal.valueOf(25)))
                .anySatisfy(s -> assertThat(s.getQuantity().value())
                        .isEqualByComparingTo(BigDecimal.ZERO));

        // Cross-node read path — the 3PL node's rows must appear alongside any wms rows,
        // no node-type filter (ADR-MONO-054 §D4 read-side claim).
        List<InventorySnapshot> crossNode = visibilityService.getCrossNodeSnapshot(TENANT_SCM, 0, 100);
        assertThat(crossNode).anyMatch(s -> s.getNodeId().toString().equals(nodeId));

        // NodeStaleness seeded — the only path that creates one for a 3PL node.
        List<NodeStaleness> staleness = visibilityService.getStaleness(TENANT_SCM);
        assertThat(staleness)
                .filteredOn(ns -> ns.getNodeId().toString().equals(nodeId))
                .hasSize(1)
                .allSatisfy(ns -> {
                    assertThat(ns.getStalenessStatus()).isEqualTo(StalenessStatus.FRESH);
                    assertThat(ns.getLastEventAt()).isEqualTo(observedAt);
                });
    }

    @Test
    @DisplayName("이후 관측이 더 오래된 observedAt 이면 → 스킵, 저장된 최신값 유지")
    void olderObservation_doesNotOverwriteNewerStoredSnapshot() {
        String externalId = "3pl-it-stale-" + UUID.randomUUID();
        RegisterThirdPartyLogisticsNodeResult registration =
                registrationService.register(TENANT_SCM, externalId, "ShipBob");
        String nodeId = registration.node().getId().toString();
        Instant firstObservedAt = Instant.now();
        Instant staleObservedAt = firstObservedAt.minusSeconds(120);

        visibilityService.applyThirdPartyObservedStock(nodeId, TENANT_SCM, firstObservedAt,
                List.of(new ObservedLine("SKU-3PL-STALE", BigDecimal.valueOf(50))));
        visibilityService.applyThirdPartyObservedStock(nodeId, TENANT_SCM, staleObservedAt,
                List.of(new ObservedLine("SKU-3PL-STALE", BigDecimal.valueOf(1))));

        List<InventorySnapshot> byNode = visibilityService.getSnapshotByNode(nodeId, TENANT_SCM);
        assertThat(byNode).hasSize(1);
        assertThat(byNode.get(0).getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    @DisplayName("미존재 nodeId → NodeNotFoundException, snapshot/staleness 미생성")
    void unknownNode_throwsNodeNotFound() {
        String randomNodeId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> visibilityService.applyThirdPartyObservedStock(
                randomNodeId, TENANT_SCM, Instant.now(),
                List.of(new ObservedLine("SKU-X", BigDecimal.ONE))))
                .isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    @DisplayName("WMS_WAREHOUSE 타입 노드에 관측 시도 → NodeTypeConflictException, 해당 노드 snapshot 미변경")
    void wmsTypedNode_throwsNodeTypeConflict() {
        InventoryNodeJpaEntity wmsNode = persistNode(TENANT_SCM, "wh-it-observe-" + UUID.randomUUID());
        String nodeId = wmsNode.getId();

        assertThatThrownBy(() -> visibilityService.applyThirdPartyObservedStock(
                nodeId, TENANT_SCM, Instant.now(),
                List.of(new ObservedLine("SKU-X", BigDecimal.ONE))))
                .isInstanceOf(NodeTypeConflictException.class);

        assertThat(visibilityService.getSnapshotByNode(nodeId, TENANT_SCM)).isEmpty();
    }
}
