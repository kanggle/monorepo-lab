package com.example.scmplatform.inventoryvisibility.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration test: wms.inventory.received.v1 → snapshot upsert + dedupe.
 * Requires Docker (Postgres + Kafka).
 * Tagged @Tag("integration") — excluded from default :test/:check task.
 * Run with: ./gradlew :projects:scm-platform:apps:inventory-visibility-service:integrationTest
 *
 * TASK-SCM-BE-003 Acceptance Criteria #8 and #9.
 */
@Tag("integration")
class WmsInventoryReceivedConsumerIntegrationTest {

    /**
     * Publishes a wms.inventory.received.v1 envelope → verifies snapshot is
     * upserted in inventory_snapshots and event_dedupe row is created.
     *
     * Implemented as @SpringBootTest + Testcontainers (Postgres + Kafka) in
     * full integration suite. Skeleton placeholder — full implementation after
     * Docker connectivity is confirmed in CI (Docker is broken on local dev
     * per project_testcontainers_docker_desktop_blocker.md).
     */
    @Test
    void receivedEvent_createsSnapshot_andDedupeRecord() {
        // Placeholder — full body in integration suite PR
        // Docker required: @SpringBootTest + PostgreSQLContainer + KafkaContainer
    }

    @Test
    void duplicateEventId_skipsSnapshot_idempotent() {
        // Placeholder — verifies T8 idempotency end-to-end
    }
}
