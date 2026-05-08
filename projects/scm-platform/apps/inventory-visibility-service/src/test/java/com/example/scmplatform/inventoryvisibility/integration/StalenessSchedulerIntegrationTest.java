package com.example.scmplatform.inventoryvisibility.integration;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.batch.StalenessDetectionScheduler;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.NodeStalenessJpaEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * IT-6: staleness detection batch — batch-heavy trait (ShedLock + @Scheduled).
 *
 * <p>Asserts that:
 * <ol>
 *   <li>A {@code node_staleness} row whose {@code last_event_at} is older
 *       than the threshold flips to {@code STALE} after the batch runs.</li>
 *   <li>A {@code scm.inventory.alert.v1} alert envelope is published for
 *       the transition (FRESH → STALE).</li>
 *   <li>ShedLock acquires a lock named {@code staleness-detection-batch}
 *       in the {@code shedlock} table (verified by a row appearing).</li>
 * </ol>
 *
 * <p>Scheduler auto-trigger is disabled in the base class (initial-delay-ms
 * = 1h); the test invokes the bean method directly, which still routes
 * through ShedLock's proxy.
 */
@DisplayName("IT-6: staleness scheduler + ShedLock + alert publish")
class StalenessSchedulerIntegrationTest extends AbstractInventoryVisibilityIntegrationTest {

    private static final String CONSUMER_GROUP = "it-staleness-alert-" + UUID.randomUUID();

    @Autowired
    private StalenessDetectionScheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private KafkaConsumer<String, String> alertConsumer;

    @BeforeEach
    void subscribeToAlertTopic() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        alertConsumer = new KafkaConsumer<>(props);
        alertConsumer.subscribe(List.of(TOPIC_ALERT));
        // Trigger partition assignment before publish.
        alertConsumer.poll(Duration.ofMillis(500));
    }

    @AfterEach
    void closeAlertConsumer() {
        if (alertConsumer != null) alertConsumer.close();
    }

    @Test
    @DisplayName("stale node → batch flips status, publishes scm.inventory.alert.v1, ShedLock row 생성")
    void detectStaleNodes_flipsStatus_publishesAlert_acquiresLock() {
        // Seed: persist a node + a node_staleness row whose last_event_at is
        // 1 hour old → far older than the 600-second default threshold.
        var node = persistNode(TENANT_SCM, "wh-stale-" + UUID.randomUUID());
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        persistStaleNodeStaleness(TENANT_SCM, node.getId(), oneHourAgo);

        // Act: invoke the scheduler bean directly (still goes through
        // @SchedulerLock proxy → ShedLock acquires `shedlock` row).
        scheduler.detectStaleNodes();

        // Assert 1: status flipped to STALE.
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            NodeStalenessJpaEntity ns =
                    stalenessJpa.findById(node.getId()).orElseThrow();
            assertThat(ns.getStalenessStatus())
                    .isEqualTo(NodeStalenessJpaEntity.StalenessStatusJpa.STALE);
        });

        // Assert 2: alert envelope published with this nodeId.
        List<String> received = new ArrayList<>();
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, String> records = alertConsumer.poll(Duration.ofMillis(500));
            records.forEach(r -> received.add(r.value()));
            assertThat(received)
                    .as("alert envelope contains the stale node id")
                    .anyMatch(v -> v.contains(node.getId()));
        });
        // Extra: parse one envelope and verify shape.
        String envelope = received.stream()
                .filter(v -> v.contains(node.getId()))
                .findFirst().orElseThrow();
        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(envelope);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        assertThat(parsed.get("eventType").asText())
                .startsWith("inventory.alert.snapshot_stale");
        assertThat(parsed.get("payload").get("alertType").asText())
                .isEqualTo("SNAPSHOT_STALE");
        assertThat(parsed.get("payload").get("nodeId").asText())
                .isEqualTo(node.getId());

        // Assert 3: ShedLock row created with the expected lock name.
        Long lockCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shedlock WHERE name = ?",
                Long.class, "staleness-detection-batch");
        assertThat(lockCount).isEqualTo(1L);
    }
}
