package com.example.security.integration;

import com.example.security.infrastructure.persistence.SuspiciousEventJpaEntity;
import com.example.security.infrastructure.persistence.SuspiciousEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * TASK-BE-248 Phase 1: cross-tenant velocity regression test.
 *
 * <p>Verifies that 50 failed logins from {@code tenant-a} for the same accountId
 * do NOT trigger the velocity detection for {@code tenant-b}. Each tenant's Redis
 * counter is keyed by {@code security:velocity:{tenantId}:{accountId}:{window}},
 * so failures for one tenant must not accumulate into another tenant's counter.</p>
 *
 * <p>Setup:
 * <ul>
 *   <li>Velocity threshold is lowered to 3 for the test so we can verify triggering
 *       quickly for tenantA and non-triggering for tenantB with a small message batch.</li>
 *   <li>Kafka events include {@code tenantId} field in the envelope so
 *       {@link com.example.security.consumer.AuthEventMapper} picks it up from the
 *       {@code Tenants.DEFAULT_TENANT_ID} fallback or an explicit field.</li>
 *   <li>No WireMock stub for account-service — the auto-lock path is irrelevant here
 *       because tenantB must NOT fire at all.</li>
 * </ul>
 * </p>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CrossTenantVelocityIntegrationTest extends AbstractIntegrationTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        // Lower threshold so 50 tenantA failures clearly exceed it.
        registry.add("security.detection.velocity.threshold", () -> "3");
        registry.add("security.detection.velocity.window-seconds", () -> "3600");
        // Disable auto-lock HTTP call — not needed for this test.
        registry.add("security.detection.auto-lock.max-attempts", () -> "0");
    }

    @Autowired private SuspiciousEventJpaRepository suspiciousEventJpaRepository;
    @Autowired private ObjectMapper objectMapper;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    /**
     * Core regression: tenantA 50 failures for accountId X must NOT trigger
     * VelocityRule for tenantB + the same accountId X.
     *
     * <p>After sending 50 tenantA failures we wait long enough that any asynchronous
     * processing has settled. Then we send 1 tenantB failure for the same accountId.
     * Only tenantA should have a {@code suspicious_events} row; tenantB's count is 1
     * (below the threshold of 3), so no row appears.</p>
     */
    @Test
    @DisplayName("[cross-tenant] tenantA 50회 실패 → tenantB 동일 account 임계치 무영향")
    void tenantABurst_doesNotTriggerTenantBDetection() {
        // Use a unique accountId to isolate this test from other runs.
        String sharedAccountId = "acc-cross-tenant-" + UUID.randomUUID();

        // ── Send 50 failed logins for tenantA ──────────────────────────────────
        for (int i = 0; i < 50; i++) {
            kafkaTemplate.send("auth.login.failed", sharedAccountId,
                    buildLoginFailedEvent(UUID.randomUUID().toString(), sharedAccountId, TENANT_A));
        }

        // Wait until tenantA's SuspiciousEvent is persisted (proves the pipeline ran).
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            List<SuspiciousEventJpaEntity> rowsA = suspiciousEventJpaRepository
                    .findByTenantIdAndAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(
                            TENANT_A, sharedAccountId,
                            Instant.now().minusSeconds(600), Instant.now().plusSeconds(60));
            assertThat(rowsA)
                    .as("tenantA must have a suspicious_events row after 50 failures")
                    .isNotEmpty();
            assertThat(rowsA.get(0).getActionTaken())
                    .as("tenantA detection action")
                    .isEqualTo("AUTO_LOCK");
        });

        // ── Send 1 failed login for tenantB (same accountId) ──────────────────
        kafkaTemplate.send("auth.login.failed", sharedAccountId,
                buildLoginFailedEvent(UUID.randomUUID().toString(), sharedAccountId, TENANT_B));

        // Allow sufficient time for tenantB event to be processed (≥ 1 consumer cycle).
        // The threshold is 3, so 1 failure must NOT fire.
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // ── Assert: tenantB has NO suspicious_events row ───────────────────────
        List<SuspiciousEventJpaEntity> rowsB = suspiciousEventJpaRepository
                .findByTenantIdAndAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(
                        TENANT_B, sharedAccountId,
                        Instant.now().minusSeconds(600), Instant.now().plusSeconds(60));
        assertThat(rowsB)
                .as("tenantB must NOT have a suspicious_events row — its counter is independent of tenantA")
                .isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a {@code auth.login.failed} event envelope with an explicit
     * {@code tenantId} field in the top-level envelope so that
     * {@link com.example.security.consumer.AuthEventMapper#toLoginHistoryEntry} and
     * {@code toEvaluationContext} pick it up instead of defaulting to
     * {@link com.example.security.domain.Tenants#DEFAULT_TENANT_ID}.
     */
    private String buildLoginFailedEvent(String eventId, String accountId, String tenantId) {
        Instant now = Instant.now();
        return """
                {
                  "eventId": "%s",
                  "eventType": "auth.login.failed",
                  "tenantId": "%s",
                  "source": "auth-service",
                  "occurredAt": "%s",
                  "schemaVersion": 1,
                  "partitionKey": "%s",
                  "payload": {
                    "accountId": "%s",
                    "tenantId": "%s",
                    "emailHash": "hash-cross-tenant",
                    "failureReason": "CREDENTIALS_INVALID",
                    "failCount": 1,
                    "ipMasked": "10.0.0.***",
                    "userAgentFamily": "Chrome 120",
                    "deviceFingerprint": "dev-cross-tenant",
                    "geoCountry": "KR",
                    "timestamp": "%s"
                  }
                }
                """.formatted(eventId, tenantId, now, accountId, accountId, tenantId, now);
    }
}
