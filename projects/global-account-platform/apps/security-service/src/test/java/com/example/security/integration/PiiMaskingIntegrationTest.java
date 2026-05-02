package com.example.security.integration;

import com.example.security.infrastructure.persistence.LoginHistoryJpaEntity;
import com.example.security.infrastructure.persistence.LoginHistoryJpaRepository;
import com.example.security.infrastructure.persistence.PiiMaskingLogJpaRepository;
import com.example.security.infrastructure.persistence.SuspiciousEventJpaEntity;
import com.example.security.infrastructure.persistence.SuspiciousEventJpaRepository;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
 * Integration test for GDPR PII masking flow (TASK-BE-258).
 *
 * <p>Verifies end-to-end: publish {@code account.deleted(anonymized=true)} →
 * {@code AccountDeletedAnonymizedConsumer} → {@code PiiMaskingService} →
 * DB rows masked + outbox entry written.
 *
 * <p>Uses real MySQL + Kafka via Testcontainers (inherited from
 * {@link AbstractIntegrationTest}). Redis is spun up locally for dedupe.
 *
 * <p>Docker required — skip gracefully on Docker-less CI hosts via the
 * Testcontainers assumption mechanism.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PiiMaskingIntegrationTest extends AbstractIntegrationTest {

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
    }

    @Autowired
    private LoginHistoryJpaRepository loginHistoryRepository;

    @Autowired
    private SuspiciousEventJpaRepository suspiciousEventRepository;

    @Autowired
    private PiiMaskingLogJpaRepository piiMaskingLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    // ─── Helper: seed login_history row ──────────────────────────────────

    private void seedLoginHistory(String tenantId, String accountId, String eventId) {
        jdbcTemplate.update("""
                INSERT INTO login_history
                  (tenant_id, event_id, account_id, outcome,
                   ip_masked, user_agent_family, device_fingerprint, geo_country, occurred_at)
                VALUES (?, ?, ?, 'SUCCESS', '192.168.1.100', 'Chrome 120', 'fp-abc123', 'KR', NOW())
                ON DUPLICATE KEY UPDATE id = id
                """, tenantId, eventId, accountId);
    }

    private void seedSuspiciousEvent(String tenantId, String accountId, String id) {
        jdbcTemplate.update("""
                INSERT INTO suspicious_events
                  (id, tenant_id, account_id, rule_code, risk_score, action_taken,
                   evidence, trigger_event_id, detected_at)
                VALUES (?, ?, ?, 'VELOCITY', 80, 'ALERT',
                        '{"description":"10 failures/hour","ip":"10.0.0.1"}', 'trig-001', NOW())
                ON DUPLICATE KEY UPDATE id = id
                """, id, tenantId, accountId);
    }

    private String buildAccountDeletedEvent(String eventId, String accountId,
                                             String tenantId, boolean anonymized) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "account.deleted",
                  "source": "account-service",
                  "occurredAt": "%s",
                  "schemaVersion": 2,
                  "partitionKey": "%s",
                  "payload": {
                    "accountId": "%s",
                    "tenantId": "%s",
                    "reasonCode": "USER_REQUEST",
                    "actorType": "user",
                    "actorId": "%s",
                    "deletedAt": "%s",
                    "anonymized": %s
                  }
                }
                """.formatted(eventId, Instant.now(), accountId,
                accountId, tenantId, accountId, Instant.now(), anonymized);
    }

    // ─── Test 1: end-to-end masking ───────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("account.deleted(anonymized=true) causes login_history PII to be masked")
    void anonymizedTrue_masksPiiInLoginHistory() {
        String tenantId  = "fan-platform";
        String accountId = "acc-mask-e2e-" + UUID.randomUUID();
        String loginEvt  = "login-" + UUID.randomUUID();
        String deleteEvt = "del-" + UUID.randomUUID();

        seedLoginHistory(tenantId, accountId, loginEvt);

        // Verify PII is present before masking.
        List<Map<String, Object>> before = jdbcTemplate.queryForList(
                "SELECT ip_masked, user_agent_family, device_fingerprint FROM login_history " +
                "WHERE tenant_id = ? AND account_id = ?", tenantId, accountId);
        assertThat(before).isNotEmpty();
        assertThat(before.get(0).get("ip_masked")).isEqualTo("192.168.1.100");

        // Publish account.deleted(anonymized=true).
        String json = buildAccountDeletedEvent(deleteEvt, accountId, tenantId, true);
        kafkaTemplate.send(new ProducerRecord<>("account.deleted", accountId, json));

        // Wait for masking to complete.
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Map<String, Object>> after = jdbcTemplate.queryForList(
                    "SELECT ip_masked, user_agent_family, device_fingerprint FROM login_history " +
                    "WHERE tenant_id = ? AND account_id = ?", tenantId, accountId);
            assertThat(after).isNotEmpty();
            assertThat(after.get(0).get("ip_masked")).isEqualTo("0.0.0.0");
            assertThat(after.get(0).get("user_agent_family")).isEqualTo("REDACTED");
            // device_fingerprint is SHA-256 of accountId (64 hex chars).
            assertThat(String.valueOf(after.get(0).get("device_fingerprint"))).hasSize(64);
        });
    }

    @Test
    @Order(2)
    @DisplayName("account.deleted(anonymized=true) causes suspicious_events evidence to be cleared")
    void anonymizedTrue_clearsSuspiciousEventEvidence() {
        String tenantId   = "fan-platform";
        String accountId  = "acc-susp-" + UUID.randomUUID();
        String suspId     = UUID.randomUUID().toString();
        String deleteEvt  = "del-susp-" + UUID.randomUUID();

        seedSuspiciousEvent(tenantId, accountId, suspId);

        // Verify evidence contains PII before masking.
        List<Map<String, Object>> before = jdbcTemplate.queryForList(
                "SELECT evidence FROM suspicious_events WHERE id = ?", suspId);
        assertThat(before).isNotEmpty();
        assertThat(String.valueOf(before.get(0).get("evidence"))).contains("10.0.0.1");

        String json = buildAccountDeletedEvent(deleteEvt, accountId, tenantId, true);
        kafkaTemplate.send(new ProducerRecord<>("account.deleted", accountId, json));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Map<String, Object>> after = jdbcTemplate.queryForList(
                    "SELECT evidence FROM suspicious_events WHERE id = ?", suspId);
            assertThat(after).isNotEmpty();
            // evidence should be cleared to empty JSON object.
            assertThat(String.valueOf(after.get(0).get("evidence"))).isEqualTo("{}");
        });
    }

    // ─── Test 2: anonymized=false is skipped ─────────────────────────────

    @Test
    @Order(3)
    @DisplayName("account.deleted(anonymized=false) does NOT trigger masking")
    void anonymizedFalse_doesNotMask() throws InterruptedException {
        String tenantId  = "fan-platform";
        String accountId = "acc-grace-" + UUID.randomUUID();
        String loginEvt  = "login-grace-" + UUID.randomUUID();
        String deleteEvt = "del-grace-" + UUID.randomUUID();

        seedLoginHistory(tenantId, accountId, loginEvt);

        String json = buildAccountDeletedEvent(deleteEvt, accountId, tenantId, false);
        kafkaTemplate.send(new ProducerRecord<>("account.deleted", accountId, json));

        // Wait briefly and assert PII is still present.
        Thread.sleep(5_000);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ip_masked FROM login_history WHERE tenant_id = ? AND account_id = ?",
                tenantId, accountId);
        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0).get("ip_masked")).isEqualTo("192.168.1.100");
    }

    // ─── Test 3: idempotency ──────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("Duplicate account.deleted(anonymized=true) results in single pii_masking_log entry")
    void duplicateEvent_onlySingleMaskingLogEntry() {
        String tenantId  = "fan-platform";
        String accountId = "acc-idem-" + UUID.randomUUID();
        String loginEvt  = "login-idem-" + UUID.randomUUID();
        String deleteEvt = "del-idem-" + UUID.randomUUID();

        seedLoginHistory(tenantId, accountId, loginEvt);

        String json = buildAccountDeletedEvent(deleteEvt, accountId, tenantId, true);
        // Send the same event twice.
        kafkaTemplate.send(new ProducerRecord<>("account.deleted", accountId, json));
        kafkaTemplate.send(new ProducerRecord<>("account.deleted", accountId, json));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = piiMaskingLogRepository.findAll().stream()
                    .filter(e -> e.getEventId().equals(deleteEvt))
                    .count();
            assertThat(count).isEqualTo(1);
        });
    }

    // ─── Test 4: outbox event written ────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("Masking writes security.pii.masked outbox entry")
    void masking_writesOutboxEntry() {
        String tenantId  = "fan-platform";
        String accountId = "acc-outbox-" + UUID.randomUUID();
        String loginEvt  = "login-outbox-" + UUID.randomUUID();
        String deleteEvt = "del-outbox-" + UUID.randomUUID();

        seedLoginHistory(tenantId, accountId, loginEvt);

        String json = buildAccountDeletedEvent(deleteEvt, accountId, tenantId, true);
        kafkaTemplate.send(new ProducerRecord<>("account.deleted", accountId, json));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Map<String, Object>> outbox = jdbcTemplate.queryForList(
                    "SELECT event_type, payload FROM outbox_events " +
                    "WHERE event_type = 'security.pii.masked' " +
                    "AND JSON_EXTRACT(payload, '$.accountId') = ?",
                    accountId);
            assertThat(outbox).isNotEmpty();
            String payload = String.valueOf(outbox.get(0).get("payload"));
            assertThat(payload).contains("\"accountId\":\"" + accountId + "\"");
            assertThat(payload).contains("\"tenantId\":\"" + tenantId + "\"");
            assertThat(payload).contains("login_history");
        });
    }

    // ─── Test 5: cross-tenant isolation ──────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("account.deleted for tenantA does not affect tenantB rows with same accountId")
    void crossTenant_tenantBRowsUnaffected() throws InterruptedException {
        String accountId = "acc-cross-" + UUID.randomUUID();
        String tenantA   = "fan-platform";
        String tenantB   = "wms";

        String loginEvtA = "login-a-" + UUID.randomUUID();
        String loginEvtB = "login-b-" + UUID.randomUUID();
        String deleteEvt = "del-cross-" + UUID.randomUUID();

        // Seed a row for each tenant with the same accountId.
        seedLoginHistory(tenantA, accountId, loginEvtA);
        seedLoginHistory(tenantB, accountId, loginEvtB);

        // Delete event is for tenantA only.
        String json = buildAccountDeletedEvent(deleteEvt, accountId, tenantA, true);
        kafkaTemplate.send(new ProducerRecord<>("account.deleted", accountId, json));

        // Wait for tenantA masking to complete.
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Map<String, Object>> rowsA = jdbcTemplate.queryForList(
                    "SELECT ip_masked FROM login_history WHERE tenant_id = ? AND account_id = ?",
                    tenantA, accountId);
            assertThat(rowsA.get(0).get("ip_masked")).isEqualTo("0.0.0.0");
        });

        // tenantB row must still have original PII.
        List<Map<String, Object>> rowsB = jdbcTemplate.queryForList(
                "SELECT ip_masked FROM login_history WHERE tenant_id = ? AND account_id = ?",
                tenantB, accountId);
        assertThat(rowsB).isNotEmpty();
        assertThat(rowsB.get(0).get("ip_masked")).isEqualTo("192.168.1.100");
    }
}
