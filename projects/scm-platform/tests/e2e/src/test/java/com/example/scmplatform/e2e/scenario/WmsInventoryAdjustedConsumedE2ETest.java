package com.example.scmplatform.e2e.scenario;

import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.authedGet;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathInventoryVisibilitySkuBreakdown;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.randomAccountId;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.randomLocationId;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.sendString;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueSku;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.scmplatform.e2e.testsupport.KafkaTestProducer;
import com.example.scmplatform.e2e.testsupport.ScmPlatformE2ETestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Scenario 6 — cross-project event consumption verified end-to-end
 * (TASK-SCM-INT-001 § In Scope #6, AC #5).
 *
 * <p>This is the keystone scenario for the monorepo Phase 4 catalyst
 * evaluation: prove that wms-platform's authoritative event envelope is
 * understood by scm-platform's inventory-visibility consumer, and that
 * idempotent dedupe (T8) holds when the same eventId is published twice.
 *
 * <p>Flow:
 *
 * <ol>
 *   <li>{@link KafkaTestProducer} (host-side) publishes a synthetic
 *       {@code wms.inventory.adjusted.v1} envelope on the shared Kafka
 *       cluster, mirroring the schema declared in
 *       {@code projects/wms-platform/specs/contracts/events/inventory-events.md}
 *       § Global Envelope. We do not boot a wms container — Failure
 *       Scenario B in the task spec ("Cross-project consumption is
 *       inherently wms-dependent" → mock answer).</li>
 *   <li>inventory-visibility-service's
 *       {@code WmsInventoryAdjustedConsumer} reads the event, resolves
 *       (auto-creates) the {@code WMS_WAREHOUSE} node, applies the delta
 *       to {@code inventory_snapshots}, and writes a dedupe row.</li>
 *   <li>The host-side test polls
 *       {@code GET /api/v1/inventory-visibility/sku/{sku}} via the gateway
 *       (read-only endpoint behind the same OIDC + tenant gate). The
 *       per-SKU breakdown now contains the synthesised quantity.</li>
 *   <li>The same {@code eventId} is re-published. The dedupe table prevents
 *       a second mutation; the SKU breakdown still shows the original
 *       quantity (T8 idempotency, AC #6).</li>
 * </ol>
 */
@Tag("full")
class WmsInventoryAdjustedConsumedE2ETest extends ScmPlatformE2ETestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("wms.inventory.adjusted.v1 published -> inventory-visibility upserts snapshot;"
            + " duplicate eventId is no-op (T8)")
    void crossProjectInventoryAdjustedFlowsToVisibilityReadModel() throws Exception {
        String operatorToken = jwt.signOperatorToken(randomAccountId());

        String locationId = randomLocationId();
        String sku = uniqueSku("SKU-WMS");
        long delta = 42L;
        UUID eventId = UUID.randomUUID();

        // ----- Step 1: publish wms event ----------------------------------
        try (KafkaTestProducer producer = new KafkaTestProducer(kafkaBootstrapForHost())) {
            producer.publishInventoryAdjusted(eventId, locationId, sku, delta);
            // TASK-SCM-INT-001b cycle-1 diagnostic: dump Kafka topic + consumer
            // group state right after the publish so the CI log carries:
            //   (a) whether topic wms.inventory.adjusted.v1 exists with the
            //       expected partition count
            //   (b) whether the consumer group `scm-inventory-visibility-v1`
            //       has registered against the topic
            //   (c) the current end-offset vs the consumer-committed offset
            //       (drives the subscribe-vs-process-fail diagnosis)
            // The ServiceContainerLogDumper TestWatcher dumps inventory-visibility
            // application logs on testFailed; this complements with broker-side
            // truth that no application log can give us.
            dumpKafkaConsumerDiagnostics(eventId);

            // ----- Step 2: snapshot read-model reflects the delta ----------
            await().atMost(Duration.ofSeconds(45))
                    .pollInterval(Duration.ofMillis(750))
                    .untilAsserted(() -> {
                        HttpResponse<String> resp = sendString(http, authedGet(
                                gatewayBaseUri().resolve(pathInventoryVisibilitySkuBreakdown(sku)),
                                operatorToken)
                                .GET().build());
                        assertThat(resp.statusCode())
                                .as("GET /api/v1/inventory-visibility/sku/{sku} -> 200")
                                .isEqualTo(200);
                        JsonNode envelope = objectMapper.readTree(resp.body());
                        JsonNode data = envelope.get("data");
                        assertThat(data).as("envelope { data, meta } has data").isNotNull();
                        assertThat(data.get("sku").asText()).isEqualTo(sku);
                        // The aggregator may fold across nodes; with a single
                        // node and a +42 adjustment the totalQuantity should
                        // be 42.
                        assertThat(data.get("totalQuantity").asDouble())
                                .as("totalQuantity reflects the delta from the wms event")
                                .isEqualTo(42.0);
                        JsonNode nodes = data.get("nodes");
                        assertThat(nodes.isArray() && nodes.size() >= 1)
                                .as("nodes array contains the auto-registered wms warehouse")
                                .isTrue();
                    });

            // ----- Step 3: republish same eventId -> dedupe -> no-op -------
            producer.publishInventoryAdjusted(eventId, locationId, sku, delta);

            // Wait for the consumer to process (and dedupe) the duplicate
            // before re-asserting the totalQuantity is unchanged.
            Thread.sleep(2_000);

            HttpResponse<String> resp = sendString(http, authedGet(
                    gatewayBaseUri().resolve(pathInventoryVisibilitySkuBreakdown(sku)),
                    operatorToken)
                    .GET().build());
            assertThat(resp.statusCode()).isEqualTo(200);
            JsonNode data = objectMapper.readTree(resp.body()).get("data");
            assertThat(data.get("totalQuantity").asDouble())
                    .as("duplicate eventId must NOT mutate the snapshot a second time (T8 dedupe)")
                    .isEqualTo(42.0);
        }
    }

    /**
     * TASK-SCM-INT-001b cycle-1 diagnostic — broker-side truth about the
     * cross-project consumer wiring. Probes:
     *
     * <ol>
     *   <li>Is {@code wms.inventory.adjusted.v1} present? (topic pre-create
     *       fix from 6ac01c5b lands in the base class — verify it stuck)</li>
     *   <li>Has consumer group {@code scm-inventory-visibility-v1} registered
     *       and what topics is each member subscribed to? (catches a
     *       group-id mismatch / missing @KafkaListener / RetryableTopic
     *       prefix diversion before the application logs do)</li>
     *   <li>End offset of partition 0 vs the group's committed offset —
     *       answers "consumer received but errored" vs "consumer never
     *       subscribed" deterministically.</li>
     * </ol>
     *
     * <p>Output is unconditional ({@code System.err}) so we get the snapshot
     * even when the test passes (so a future regression is comparable).
     */
    private void dumpKafkaConsumerDiagnostics(UUID eventId) {
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapForHost());
        try (AdminClient admin = AdminClient.create(adminProps)) {
            System.err.println("[INT-001b][wms] === Kafka diagnostic for eventId=" + eventId + " ===");

            try {
                System.err.println("[INT-001b][wms] topics: " + admin.listTopics().names()
                        .get(10, TimeUnit.SECONDS));
            } catch (Exception e) {
                System.err.println("[INT-001b][wms] listTopics failed: " + e.getMessage());
            }

            String groupId = "scm-inventory-visibility-v1";
            try {
                java.util.Map<String, ConsumerGroupDescription> groups = admin
                        .describeConsumerGroups(java.util.List.of(groupId))
                        .all().get(10, TimeUnit.SECONDS);
                ConsumerGroupDescription desc = groups.get(groupId);
                System.err.println("[INT-001b][wms] group=" + groupId
                        + " state=" + (desc == null ? "<null>" : desc.state())
                        + " members=" + (desc == null ? 0 : desc.members().size()));
                if (desc != null) {
                    desc.members().forEach(m ->
                            System.err.println("[INT-001b][wms]   member=" + m.consumerId()
                                    + " host=" + m.host()
                                    + " assignment=" + m.assignment().topicPartitions()));
                }
            } catch (Exception e) {
                System.err.println("[INT-001b][wms] describeConsumerGroups failed: "
                        + e.getMessage());
            }

            TopicPartition tp = new TopicPartition("wms.inventory.adjusted.v1", 0);
            try {
                java.util.Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> end =
                        admin.listOffsets(java.util.Map.of(tp,
                                org.apache.kafka.clients.admin.OffsetSpec.latest()))
                                .all().get(10, TimeUnit.SECONDS);
                System.err.println("[INT-001b][wms] endOffset(" + tp + ")="
                        + (end.get(tp) == null ? "<null>" : end.get(tp).offset()));
            } catch (Exception e) {
                System.err.println("[INT-001b][wms] listOffsets failed: " + e.getMessage());
            }

            try {
                ListConsumerGroupOffsetsResult committed = admin.listConsumerGroupOffsets(groupId);
                java.util.Map<TopicPartition, OffsetAndMetadata> offsets = committed
                        .partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
                System.err.println("[INT-001b][wms] committedOffsets group=" + groupId
                        + " entries=" + offsets.size() + " " + offsets);
            } catch (Exception e) {
                System.err.println("[INT-001b][wms] listConsumerGroupOffsets failed: "
                        + e.getMessage());
            }
            System.err.println("[INT-001b][wms] === end Kafka diagnostic ===");
        } catch (Exception e) {
            System.err.println("[INT-001b][wms] AdminClient unavailable: " + e.getMessage());
        }
    }
}
