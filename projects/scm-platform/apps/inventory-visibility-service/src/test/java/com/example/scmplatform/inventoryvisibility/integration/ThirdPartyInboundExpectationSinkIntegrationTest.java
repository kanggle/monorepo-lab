package com.example.scmplatform.inventoryvisibility.integration;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InboundExpectationJpaEntity;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InboundExpectationJpaRepository;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService.ExpectedLine;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService.ObservedLine;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * TASK-SCM-BE-049 — real-Postgres + Kafka IT for the 3PL inbound-expectation honour
 * sink (ADR-MONO-055 §D4): the {@code scm.procurement.inbound-expected.third-party.v1}
 * event is consumed and recorded against the registered 3PL node, is idempotent on the
 * PO reference, is reconciled by a later 3PL observation (TASK-SCM-BE-047), and
 * fail-closes on an absent node (no orphan expectation).
 *
 * <p>CI-only per {@code AbstractInventoryVisibilityIntegrationTest} (Testcontainers +
 * {@code DockerAvailableCondition}) — Windows host cannot run this locally; compiles and
 * is exercised by the CI Integration lane.
 */
@Tag("integration")
@DisplayName("IT: 3PL inbound-expected honour sink (record via event + reconcile + idempotency + fail-closed)")
class ThirdPartyInboundExpectationSinkIntegrationTest extends AbstractInventoryVisibilityIntegrationTest {

    @Autowired
    RegisterThirdPartyLogisticsNodeService registrationService;

    @Autowired
    InventoryVisibilityApplicationService visibilityService;

    @Autowired
    InboundExpectationJpaRepository expectationJpa;

    @Test
    @DisplayName("이벤트 소비 → 3PL 노드에 OPEN 기대입고 기록; 재발행은 멱등(중복 미기록)")
    void event_recordsOpenExpectationAgainstNode_andIsIdempotentOnPoReference() {
        String externalId = "3pl-it-sink-" + UUID.randomUUID();
        String nodeId = registrationService.register(TENANT_SCM, externalId, "품고 물류센터")
                .node().getId().toString();
        String poNumber = shortPoNumber();
        String sku = "SKU-SINK-001";

        publish(TOPIC_INBOUND_EXPECTED_THIRD_PARTY, "po-sink",
                thirdPartyExpectedEnvelope("po-sink", poNumber, TENANT_SCM, nodeId,
                        "2026-08-10", sku, "100"));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            List<InboundExpectationJpaEntity> rows = expectationsFor(nodeId, poNumber);
            assertThat(rows).hasSize(1);
            InboundExpectationJpaEntity row = rows.get(0);
            assertThat(row.getSku()).isEqualTo(sku);
            assertThat(row.getExpectedQuantity()).isEqualByComparingTo(new BigDecimal("100"));
            assertThat(row.getStatus()).isEqualTo(InboundExpectationJpaEntity.ExpectationStatusJpa.OPEN);
            assertThat(row.getExpectedAt()).isEqualTo(LocalDate.parse("2026-08-10"));
        });

        // Replay the same event — idempotent on (tenant, po_number, sku, node): no second row.
        publish(TOPIC_INBOUND_EXPECTED_THIRD_PARTY, "po-sink",
                thirdPartyExpectedEnvelope("po-sink", poNumber, TENANT_SCM, nodeId,
                        "2026-08-10", sku, "100"));

        // Give the consumer time to (not) double-record, then assert still exactly one row.
        await().during(3, TimeUnit.SECONDS).atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(expectationsFor(nodeId, poNumber)).hasSize(1));
    }

    @Test
    @DisplayName("이후 3PL 관측이 기대수량 충족 → 기대입고 SATISFIED 로 정합")
    void laterObservation_meetingExpectedQty_reconcilesExpectationToSatisfied() {
        String externalId = "3pl-it-reconcile-" + UUID.randomUUID();
        String nodeId = registrationService.register(TENANT_SCM, externalId, "ShipBob")
                .node().getId().toString();
        String poNumber = shortPoNumber();
        String sku = "SKU-RECON-001";

        visibilityService.recordThirdPartyInboundExpectation(
                nodeId, TENANT_SCM, "po-recon", poNumber, LocalDate.parse("2026-08-10"),
                List.of(new ExpectedLine(sku, new BigDecimal("100"))));

        assertThat(expectationsFor(nodeId, poNumber))
                .singleElement()
                .satisfies(r -> assertThat(r.getStatus())
                        .isEqualTo(InboundExpectationJpaEntity.ExpectationStatusJpa.OPEN));

        // Observe stock >= expected → binary satisfy.
        visibilityService.applyThirdPartyObservedStock(nodeId, TENANT_SCM, Instant.now(),
                List.of(new ObservedLine(sku, new BigDecimal("120"))));

        assertThat(expectationsFor(nodeId, poNumber))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getStatus())
                            .isEqualTo(InboundExpectationJpaEntity.ExpectationStatusJpa.SATISFIED);
                    assertThat(r.getSatisfiedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("관측이 기대수량 미달 → 기대입고 OPEN 유지(가시적 미충족 신호)")
    void laterObservation_belowExpectedQty_leavesExpectationOpen() {
        String externalId = "3pl-it-partial-" + UUID.randomUUID();
        String nodeId = registrationService.register(TENANT_SCM, externalId, "품고")
                .node().getId().toString();
        String poNumber = shortPoNumber();
        String sku = "SKU-PARTIAL-001";

        visibilityService.recordThirdPartyInboundExpectation(
                nodeId, TENANT_SCM, "po-partial", poNumber, null,
                List.of(new ExpectedLine(sku, new BigDecimal("100"))));

        visibilityService.applyThirdPartyObservedStock(nodeId, TENANT_SCM, Instant.now(),
                List.of(new ObservedLine(sku, new BigDecimal("40"))));

        assertThat(expectationsFor(nodeId, poNumber))
                .singleElement()
                .satisfies(r -> assertThat(r.getStatus())
                        .isEqualTo(InboundExpectationJpaEntity.ExpectationStatusJpa.OPEN));
    }

    @Test
    @DisplayName("미존재 노드로 기록 시도 → NodeNotFoundException, orphan 기대입고 미생성(fail-closed)")
    void absentNode_failsClosed_noOrphanExpectation() {
        String randomNodeId = UUID.randomUUID().toString();
        String poNumber = shortPoNumber();

        assertThatThrownBy(() -> visibilityService.recordThirdPartyInboundExpectation(
                randomNodeId, TENANT_SCM, "po-orphan", poNumber, null,
                List.of(new ExpectedLine("SKU-X", new BigDecimal("10")))))
                .isInstanceOf(NodeNotFoundException.class);

        assertThat(expectationsFor(randomNodeId, poNumber)).isEmpty();
    }

    /** A po_number within the production VARCHAR(40) width (matches procurement's "PO-"+8hex). */
    private static String shortPoNumber() {
        return "PO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private List<InboundExpectationJpaEntity> expectationsFor(String nodeId, String poNumber) {
        return expectationJpa.findAll().stream()
                .filter(e -> e.getNodeId().equals(nodeId) && e.getSourcePoNumber().equals(poNumber))
                .toList();
    }

    /** Build the procurement 3PL inbound-expected envelope JSON (one line). */
    private String thirdPartyExpectedEnvelope(String poId, String poNumber, String tenantId,
                                              String nodeId, String expectedArrivalDate,
                                              String skuCode, String expectedQty) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("skuCode", skuCode);
        line.put("expectedQty", expectedQty);
        line.put("uom", "EA");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("poId", poId);
        payload.put("poNumber", poNumber);
        payload.put("tenantId", tenantId);
        payload.put("destinationNodeId", nodeId);
        payload.put("destinationNodeType", "THIRD_PARTY_LOGISTICS");
        payload.put("expectedArrivalDate", expectedArrivalDate);
        payload.put("currency", "KRW");
        payload.put("lines", List.of(line));

        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", UUID.randomUUID().toString());
        env.put("eventType", "scm.procurement.inbound-expected.third-party");
        env.put("source", "scm-platform-procurement-service");
        env.put("occurredAt", Instant.now().toString());
        env.put("schemaVersion", 1);
        env.put("partitionKey", poId);
        env.put("payload", payload);
        try {
            return objectMapper.writeValueAsString(env);
        } catch (Exception e) {
            throw new IllegalStateException("envelope serialise failed", e);
        }
    }
}
