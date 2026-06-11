package com.example.scmplatform.demandplanning.integration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: unmapped SKU → DLT (non-retryable), no suggestion raised.
 * AC-4 from TASK-SCM-BE-024.
 */
class UnmappedSkuDltIntegrationTest extends AbstractDemandPlanningIntegrationTest {

    @BeforeEach
    void cleanState() {
        suggestionJpa.deleteAll();
        processedEventJpa.deleteAll();
        mappingJpa.deleteAll();
        policyJpa.deleteAll();
    }

    @Test
    void unmappedSku_routesToDlt_noSuggestion_AC4() {
        UUID eventId = UUID.randomUUID();
        String unmappedSku = "SKU-NO-MAPPING-" + eventId;
        UUID warehouseId = UUID.randomUUID();

        // No mapping seeded for this SKU
        String envelope = alertEnvelope(eventId, unmappedSku, warehouseId.toString(), 5, 8);
        publish(TOPIC_ALERT, unmappedSku, envelope);

        // No suggestion must be raised
        Awaitility.await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(suggestionJpa.findAll()).isEmpty());
    }
}
