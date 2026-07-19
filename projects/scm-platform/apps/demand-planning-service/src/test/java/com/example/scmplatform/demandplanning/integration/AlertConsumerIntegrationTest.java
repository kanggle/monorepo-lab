package com.example.scmplatform.demandplanning.integration;

import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ReorderPolicyJpaEntity;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ReorderSuggestionJpaEntity;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.SkuSupplierMappingJpaEntity;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: alert consumer → suggestion upsert + T8 dedup + D6 open-guard.
 * AC-2, AC-3 from TASK-SCM-BE-024.
 */
class AlertConsumerIntegrationTest extends AbstractDemandPlanningIntegrationTest {

    static final String SKU = "SKU-APPLE-IT-001";
    static final UUID WAREHOUSE_ID = UUID.randomUUID();
    // ADR-MONO-050 D9: supplierId is a supplier CODE (String).
    static final String SUPPLIER_ID = "SUP-IT-001";

    @BeforeEach
    void seedMapping() {
        // Seed sku_supplier_map
        SkuSupplierMappingJpaEntity mapping = new SkuSupplierMappingJpaEntity();
        mapping.setTenantId(TENANT_SCM);
        mapping.setSkuCode(SKU);
        mapping.setSupplierId(SUPPLIER_ID);
        mapping.setDefaultOrderQty(200);
        mapping.setLeadTimeDays(7);
        mapping.setCurrency("KRW");
        mappingJpa.save(mapping);

        // Seed reorder_policy (reorderPoint=10, reorderQty=100)
        ReorderPolicyJpaEntity policy = new ReorderPolicyJpaEntity();
        policy.setTenantId(TENANT_SCM);
        policy.setSkuCode(SKU);
        policy.setReorderPoint(10);
        policy.setSafetyStock(5);
        policy.setReorderQty(100);
        policy.setVersion(0);
        policy.setUpdatedAt(Instant.now());
        policyJpa.save(policy);
    }

    @BeforeEach
    void cleanSuggestions() {
        suggestionJpa.deleteAll();
        processedEventJpa.deleteAll();
    }

    @Test
    void alertBelowReorderPoint_raisesOneSuggestion_AC2() {
        UUID eventId = UUID.randomUUID();
        String envelope = alertEnvelope(eventId, SKU, WAREHOUSE_ID.toString(), 5, 8);

        publish(TOPIC_ALERT, SKU, envelope);

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<ReorderSuggestionJpaEntity> suggestions = suggestionJpa.findAll();
            assertThat(suggestions).hasSize(1);
            ReorderSuggestionJpaEntity s = suggestions.get(0);
            assertThat(s.getSkuCode()).isEqualTo(SKU);
            assertThat(s.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
            // ADR-MONO-050 D9: the additive warehouse CODE is threaded onto the suggestion.
            assertThat(s.getWarehouseCode()).isEqualTo(ALERT_WAREHOUSE_CODE);
            assertThat(s.getSupplierId()).isEqualTo(SUPPLIER_ID);
            assertThat(s.getStatus()).isEqualTo(SuggestionStatus.SUGGESTED);
            assertThat(s.getTriggerAvailableQty()).isEqualTo(5);
            assertThat(s.getSuggestedQty()).isEqualTo(100);
        });

        // T8: processed_events row must exist
        assertThat(processedEventJpa.existsByEventId(eventId)).isTrue();
    }

    @Test
    void sameEventIdTwice_raisesOnlyOneSuggestion_T8Dedup_AC3() {
        UUID eventId = UUID.randomUUID();
        String envelope = alertEnvelope(eventId, SKU, WAREHOUSE_ID.toString(), 5, 8);

        publish(TOPIC_ALERT, SKU, envelope);
        // Wait for first to settle, then publish again
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() ->
                suggestionJpa.findAll().size() == 1);

        publish(TOPIC_ALERT, SKU, envelope); // same eventId

        // Wait a bit and confirm still exactly 1 suggestion
        Awaitility.await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(suggestionJpa.findAll()).hasSize(1));
    }

    @Test
    void openSuggestionExists_blocksDuplicate_D6Guard_AC3() {
        // First alert raises a suggestion
        UUID eventId1 = UUID.randomUUID();
        publish(TOPIC_ALERT, SKU, alertEnvelope(eventId1, SKU, WAREHOUSE_ID.toString(), 5, 8));
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() ->
                suggestionJpa.findAll().size() == 1);

        // Second alert (different eventId, same SKU+warehouse) should be blocked by D6
        UUID eventId2 = UUID.randomUUID();
        publish(TOPIC_ALERT, SKU, alertEnvelope(eventId2, SKU, WAREHOUSE_ID.toString(), 4, 8));

        Awaitility.await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(suggestionJpa.findAll()).hasSize(1));
    }

    @Test
    void alertWithoutWarehouseCode_stillRaisesSuggestion_degradesGracefully_BE038() {
        // ADR-MONO-050 D9 / TASK-SCM-BE-038: warehouseCode is ADDITIVE — an absent code
        // must NOT DLT the alert (that would break the pre-existing ADR-027 loop). The
        // suggestion is still raised (warehouseCode=null); only the downstream
        // inbound-expected leg fail-closes, exactly like the batch-sweep path.
        UUID eventId = UUID.randomUUID();
        String envelope = alertEnvelope(eventId, SKU, WAREHOUSE_ID.toString(), 5, 8, null);

        publish(TOPIC_ALERT, SKU, envelope);

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<ReorderSuggestionJpaEntity> suggestions = suggestionJpa.findAll();
            assertThat(suggestions).hasSize(1);
            ReorderSuggestionJpaEntity s = suggestions.get(0);
            assertThat(s.getSkuCode()).isEqualTo(SKU);
            assertThat(s.getWarehouseCode()).isNull();          // absent → null, not DLT
            assertThat(s.getStatus()).isEqualTo(SuggestionStatus.SUGGESTED);
        });
        // Processed (not routed to DLT).
        assertThat(processedEventJpa.existsByEventId(eventId)).isTrue();
    }

    @Test
    void aboveReorderPoint_noSuggestionRaised() {
        UUID eventId = UUID.randomUUID();
        // availableQty=50, reorderPoint=10 → no raise
        String envelope = alertEnvelope(eventId, SKU, WAREHOUSE_ID.toString(), 50, 8);
        publish(TOPIC_ALERT, SKU, envelope);

        Awaitility.await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(suggestionJpa.findAll()).isEmpty());

        // Event still marked processed
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> processedEventJpa.existsByEventId(eventId));
    }
}
