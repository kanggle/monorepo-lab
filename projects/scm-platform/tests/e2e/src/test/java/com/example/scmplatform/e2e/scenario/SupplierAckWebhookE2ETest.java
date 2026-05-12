package com.example.scmplatform.e2e.scenario;

import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.authedJson;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathProcurementPo;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathProcurementPoSubmit;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathSupplierAckWebhook;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.randomAccountId;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.sendString;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueIdempotencyKey;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueSku;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueSupplierAckRef;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.webhookJson;
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
 * Scenario 2 — supplier ack webhook flow (TASK-SCM-INT-001 § In Scope #2).
 *
 * <p>Builds on the happy path: after a PO reaches SUBMITTED, the supplier
 * dispatches an asynchronous webhook
 * ({@code POST /api/procurement/webhooks/supplier-ack}) to acknowledge
 * receipt. This webhook lands directly on procurement-service (the gateway
 * does not front {@code /api/procurement/webhooks/**} — the path is auth-
 * gated by a shared secret per {@code rules/traits/integration-heavy.md} I6
 * v1 simplification, with HMAC + timestamp + nonce coming in v2).
 *
 * <p>Verifies:
 * <ul>
 *   <li>The PO transitions to ACKNOWLEDGED with the supplied
 *       {@code supplierAckRef}.</li>
 *   <li>{@code scm.procurement.po.acknowledged.v1} is published via the
 *       outbox relay with the same supplierAckRef in the payload.</li>
 * </ul>
 */
@Tag("full")
class SupplierAckWebhookE2ETest extends ScmPlatformE2ETestBase {

    private static final String TOPIC_PO_ACKNOWLEDGED = "scm.procurement.po.acknowledged.v1";

    /** Default shared secret declared in procurement-service application.yml. */
    private static final String SUPPLIER_WEBHOOK_SECRET = "scm-supplier-webhook-secret";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("submitted PO + supplier ack webhook -> ACKNOWLEDGED + po.acknowledged.v1")
    void supplierAckWebhookTransitionsAndPublishesEvent() throws Exception {
        // ----- Identity + supplier seed ------------------------------------
        String buyerAccountId = randomAccountId();
        String buyerToken = jwt.signBuyerToken(buyerAccountId);
        String supplierId = ProcurementDbFixtures.insertActiveSupplier(
                postgres, "scm", "Acme Supplier (e2e-ack)");

        String sku = uniqueSku("SKU-ACK");
        String supplierAckRef = uniqueSupplierAckRef("ACK");

        try (KafkaTestConsumer consumer = new KafkaTestConsumer(kafkaBootstrapForHost(),
                List.of(TOPIC_PO_ACKNOWLEDGED))) {

            // ==============================================================
            // Bring the PO to SUBMITTED first.
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
                          "quantity": 7,
                          "unitPrice": 12.50
                        }
                      ]
                    }
                    """.formatted(supplierId, sku, sku);

            HttpResponse<String> draftResp = sendString(http, authedJson(
                    gatewayBaseUri().resolve(pathProcurementPo()), buyerToken)
                    .header("Idempotency-Key", uniqueIdempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofString(draftBody))
                    .build());
            assertThat(draftResp.statusCode()).isEqualTo(201);
            String poId = objectMapper.readTree(draftResp.body()).get("data").get("id").asText();

            supplierMock.enqueueSuccess("RCPT-ACK-" + supplierAckRef);
            HttpResponse<String> submitResp = sendString(http, authedJson(
                    gatewayBaseUri().resolve(pathProcurementPoSubmit(poId)), buyerToken)
                    .header("Idempotency-Key", uniqueIdempotencyKey())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build());
            assertThat(submitResp.statusCode()).isEqualTo(200);

            // ==============================================================
            // Step — supplier dispatches the ack webhook (direct to service)
            // ==============================================================
            String webhookBody = """
                    {
                      "tenantId": "scm",
                      "poId": "%s",
                      "supplierAckRef": "%s"
                    }
                    """.formatted(poId, supplierAckRef);

            HttpResponse<String> ackResp = sendString(http, webhookJson(
                    procurementBaseUri().resolve(pathSupplierAckWebhook()), SUPPLIER_WEBHOOK_SECRET)
                    .POST(HttpRequest.BodyPublishers.ofString(webhookBody))
                    .build());

            assertThat(ackResp.statusCode())
                    .as("supplier ack webhook returns 200 with valid signature")
                    .isEqualTo(200);
            JsonNode ackJson = objectMapper.readTree(ackResp.body());
            assertThat(ackJson.get("data").get("status").asText()).isEqualTo("ACKNOWLEDGED");

            // ==============================================================
            // Outbox -> Kafka assertion
            // ==============================================================
            List<ConsumerRecord<String, String>> seenAcc = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        seenAcc.addAll(consumer.drain());
                        ConsumerRecord<String, String> match = seenAcc.stream()
                                .filter(r -> TOPIC_PO_ACKNOWLEDGED.equals(r.topic()))
                                .filter(r -> poId.equals(r.key()))
                                .findFirst().orElse(null);
                        assertThat(match)
                                .as(TOPIC_PO_ACKNOWLEDGED + " envelope keyed by PO id arrives within 30 s")
                                .isNotNull();
                        JsonNode envelope = objectMapper.readTree(match.value());
                        assertThat(envelope.get("eventType").asText())
                                .isEqualTo("scm.procurement.po.acknowledged");
                        JsonNode payload = envelope.get("payload");
                        assertThat(payload.get("poId").asText()).isEqualTo(poId);
                        assertThat(payload.get("supplierAckRef").asText()).isEqualTo(supplierAckRef);
                    });
        }
    }
}
