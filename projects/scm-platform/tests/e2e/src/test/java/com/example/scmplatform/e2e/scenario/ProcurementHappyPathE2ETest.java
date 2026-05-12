package com.example.scmplatform.e2e.scenario;

import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.authedJson;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathProcurementPo;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathProcurementPoSubmit;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.randomAccountId;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.sendString;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueIdempotencyKey;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueSku;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.scmplatform.e2e.testsupport.KafkaTestConsumer;
import com.example.scmplatform.e2e.testsupport.ProcurementDbFixtures;
import com.example.scmplatform.e2e.testsupport.ScmPlatformE2ETestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Scenario 1 — procurement happy path through the gateway with all 3 v1
 * services live (TASK-SCM-INT-001 § In Scope #1).
 *
 * <p>Flow:
 *
 * <ol>
 *   <li>Buyer JWT issues {@code POST /api/v1/procurement/po} (DRAFT).</li>
 *   <li>Buyer submits via {@code POST /api/v1/procurement/po/{id}/submit}.
 *       Supplier WireMock returns 200 with a synthetic receipt ref.</li>
 *   <li>Outbox row is written in the same DB transaction as the SUBMITTED
 *       state transition (T2 + T3).</li>
 *   <li>{@code ProcurementOutboxPollingScheduler} picks the row up within
 *       its 500 ms polling window and publishes
 *       {@code scm.procurement.po.submitted.v1} to Kafka.</li>
 *   <li>The host-side {@link KafkaTestConsumer} sees the envelope keyed by
 *       the PO id; payload echoes the unique sku / supplier id.</li>
 * </ol>
 *
 * <p>Validates: gateway tenant gate (BUYER token with {@code tenant_id=scm})
 * forwards to procurement-service, RewritePath strips the {@code /v1/} prefix,
 * supplier adapter calls the host-side mock through {@code host.docker.internal},
 * the outbox relay publishes the canonical event topic.
 */
@Tag("smoke")
class ProcurementHappyPathE2ETest extends ScmPlatformE2ETestBase {

    private static final String TOPIC_PO_SUBMITTED = "scm.procurement.po.submitted.v1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("buyer creates DRAFT PO -> submit -> outbox -> Kafka po.submitted.v1")
    void happyPathDraftSubmitAndOutboxRelay() throws Exception {
        // ----- Identity ----------------------------------------------------
        String buyerAccountId = randomAccountId();
        String buyerToken = jwt.signBuyerToken(buyerAccountId);

        // ----- Supplier seed (suppliers are v1 internal master) ------------
        String supplierId = ProcurementDbFixtures.insertActiveSupplier(
                postgres, "scm", "Acme Supplier (e2e-happy)");

        // ----- Unique fixtures (race avoidance per task spec § Edge Cases) -
        String sku = uniqueSku("SKU-HAP");
        String draftKey = uniqueIdempotencyKey();
        String submitKey = uniqueIdempotencyKey();

        // Pre-subscribe so we don't miss the Kafka publish that lands during
        // the supplier adapter call.
        try (KafkaTestConsumer consumer = new KafkaTestConsumer(kafkaBootstrapForHost(),
                List.of(TOPIC_PO_SUBMITTED))) {

            // ==============================================================
            // Step 1 — buyer drafts a PO with one line
            // ==============================================================
            String draftBody = """
                    {
                      "supplierId": "%s",
                      "currency": "USD",
                      "lines": [
                        {
                          "lineNo": 1,
                          "sku": "%s",
                          "supplierSku": "SUP-%s",
                          "quantity": 10,
                          "unitPrice": 5.00
                        }
                      ]
                    }
                    """.formatted(supplierId, sku, sku);

            HttpResponse<String> draftResp = sendString(http, authedJson(
                    gatewayBaseUri().resolve(pathProcurementPo()), buyerToken)
                    .header("Idempotency-Key", draftKey)
                    .POST(HttpRequest.BodyPublishers.ofString(draftBody))
                    .build());

            assertThat(draftResp.statusCode())
                    .as("POST /api/v1/procurement/po returns 201 for BUYER role")
                    .isEqualTo(201);
            JsonNode draftJson = objectMapper.readTree(draftResp.body());
            JsonNode draftData = draftJson.get("data");
            assertThat(draftData).as("envelope { data, meta } has data").isNotNull();
            assertThat(draftData.get("status").asText()).isEqualTo("DRAFT");
            assertThat(draftData.get("supplierId").asText()).isEqualTo(supplierId);
            String poId = draftData.get("id").asText();
            assertThat(poId).as("DRAFT PO carries an id").isNotBlank();

            // ==============================================================
            // Step 2 — supplier ack stub + buyer submits
            // ==============================================================
            String supplierReceiptRef = "RCPT-HAP-" + System.currentTimeMillis();
            supplierMock.enqueueSuccess(supplierReceiptRef);

            HttpResponse<String> submitResp = sendString(http, authedJson(
                    gatewayBaseUri().resolve(pathProcurementPoSubmit(poId)), buyerToken)
                    .header("Idempotency-Key", submitKey)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build());

            assertThat(submitResp.statusCode())
                    .as("POST /api/v1/procurement/po/{id}/submit returns 200 after supplier ACK")
                    .isEqualTo(200);
            JsonNode submitJson = objectMapper.readTree(submitResp.body());
            assertThat(submitJson.get("data").get("status").asText()).isEqualTo("SUBMITTED");

            // ==============================================================
            // Step 3 — outbox -> Kafka assertion
            // ==============================================================
            List<ConsumerRecord<String, String>> seenAcc = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        seenAcc.addAll(consumer.drain());
                        ConsumerRecord<String, String> match = seenAcc.stream()
                                .filter(r -> TOPIC_PO_SUBMITTED.equals(r.topic()))
                                .filter(r -> poId.equals(r.key()))
                                .findFirst().orElse(null);
                        assertThat(match)
                                .as(TOPIC_PO_SUBMITTED + " envelope keyed by PO id arrives within 30 s")
                                .isNotNull();
                        JsonNode envelope = objectMapper.readTree(match.value());
                        assertThat(envelope.get("eventType").asText())
                                .as("envelope.eventType == scm.procurement.po.submitted")
                                .isEqualTo("scm.procurement.po.submitted");
                        // The libs/java-messaging BaseEventPublisher writes
                        // the source under the field name "source" (verified
                        // by the procurement-service IT-2 outbox test).
                        JsonNode payload = envelope.get("payload");
                        assertThat(payload.get("poId").asText()).isEqualTo(poId);
                        assertThat(payload.get("supplierId").asText()).isEqualTo(supplierId);
                        assertThat(payload.get("tenantId").asText()).isEqualTo("scm");
                    });
        }
    }
}
