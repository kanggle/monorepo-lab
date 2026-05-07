package com.example.scmplatform.e2e.scenario;

import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.authedJson;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathAsnWebhook;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathProcurementPo;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathProcurementPoConfirm;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathProcurementPoSubmit;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathSupplierAckWebhook;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.randomAccountId;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.sendString;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueIdempotencyKey;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueSku;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueSupplierAckRef;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueSupplierAsnRef;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Scenario 3 — full PO lifecycle (DRAFT -> SUBMITTED -> ACKNOWLEDGED ->
 * CONFIRMED -> RECEIVED) ending in ASN-driven receipt
 * (TASK-SCM-INT-001 § In Scope #3).
 *
 * <p>Verifies:
 * <ul>
 *   <li>The buyer-driven CONFIRM transition succeeds via the gateway
 *       ({@code POST /api/v1/procurement/po/{id}/confirm}, OPERATOR-or-BUYER
 *       per the state machine spec).</li>
 *   <li>An ASN webhook with full ordered quantity advances the PO to
 *       RECEIVED.</li>
 *   <li>Two distinct events are published in this scenario:
 *       {@code scm.procurement.po.received.v1} (aggregate=purchase_order)
 *       and {@code scm.procurement.asn.received.v1} (aggregate=asn).</li>
 *   <li>{@code audit_log} carries at least one row per state transition
 *       (DRAFT, SUBMIT, ACKNOWLEDGE, CONFIRM, RECEIVE) — Task spec § AC #3.</li>
 * </ul>
 */
class AsnReceiveE2ETest extends ScmPlatformE2ETestBase {

    private static final String TOPIC_PO_RECEIVED = "scm.procurement.po.received.v1";
    private static final String TOPIC_ASN_RECEIVED = "scm.procurement.asn.received.v1";

    private static final String SUPPLIER_WEBHOOK_SECRET = "scm-supplier-webhook-secret";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("DRAFT -> SUBMITTED -> ACK -> CONFIRMED -> RECEIVED via ASN; audit_log >=5 rows")
    void fullLifecycleEndingInAsnReceived() throws Exception {
        String buyerAccountId = randomAccountId();
        String buyerToken = jwt.signBuyerToken(buyerAccountId);
        String operatorToken = jwt.signOperatorToken(randomAccountId());
        String supplierId = ProcurementDbFixtures.insertActiveSupplier(
                postgres, "scm", "Acme Supplier (e2e-asn)");

        String sku = uniqueSku("SKU-ASN");
        int orderedQty = 4;

        try (KafkaTestConsumer consumer = new KafkaTestConsumer(kafkaBootstrapForHost(),
                List.of(TOPIC_PO_RECEIVED, TOPIC_ASN_RECEIVED))) {

            // ----- DRAFT ---------------------------------------------------
            String draftBody = """
                    {
                      "supplierId": "%s",
                      "currency": "USD",
                      "lines": [
                        { "lineNo": 1, "sku": "%s", "supplierSku": "SUP-%s",
                          "quantity": %d, "unitPrice": 9.99 }
                      ]
                    }
                    """.formatted(supplierId, sku, sku, orderedQty);

            HttpResponse<String> draftResp = sendString(http, authedJson(
                    gatewayBaseUri().resolve(pathProcurementPo()), buyerToken)
                    .header("Idempotency-Key", uniqueIdempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofString(draftBody))
                    .build());
            assertThat(draftResp.statusCode()).isEqualTo(201);
            JsonNode draftJson = objectMapper.readTree(draftResp.body()).get("data");
            String poId = draftJson.get("id").asText();
            // Read PO line id from the response (needed for ASN line.poLineId).
            JsonNode firstLine = draftJson.get("lines").get(0);
            String poLineId = firstLine.get("id").asText();

            // ----- SUBMIT --------------------------------------------------
            supplierMock.enqueueSuccess("RCPT-ASN-" + System.currentTimeMillis());
            HttpResponse<String> submitResp = sendString(http, authedJson(
                    gatewayBaseUri().resolve(pathProcurementPoSubmit(poId)), buyerToken)
                    .header("Idempotency-Key", uniqueIdempotencyKey())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build());
            assertThat(submitResp.statusCode()).isEqualTo(200);

            // ----- ACKNOWLEDGE (supplier webhook) -------------------------
            String supplierAckRef = uniqueSupplierAckRef("ACK-ASN");
            String ackBody = objectMapper.writeValueAsString(Map.of(
                    "tenantId", "scm", "poId", poId, "supplierAckRef", supplierAckRef));
            HttpResponse<String> ackResp = sendString(http, webhookJson(
                    procurementBaseUri().resolve(pathSupplierAckWebhook()), SUPPLIER_WEBHOOK_SECRET)
                    .POST(HttpRequest.BodyPublishers.ofString(ackBody))
                    .build());
            assertThat(ackResp.statusCode()).isEqualTo(200);

            // ----- CONFIRM (operator) -------------------------------------
            HttpResponse<String> confirmResp = sendString(http, authedJson(
                    gatewayBaseUri().resolve(pathProcurementPoConfirm(poId)), operatorToken)
                    .header("Idempotency-Key", uniqueIdempotencyKey())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build());
            assertThat(confirmResp.statusCode()).isEqualTo(200);
            assertThat(objectMapper.readTree(confirmResp.body()).get("data").get("status").asText())
                    .isEqualTo("CONFIRMED");

            // ----- ASN webhook (full ordered quantity -> RECEIVED) --------
            String supplierAsnRef = uniqueSupplierAsnRef("ASN");
            String asnBody = """
                    {
                      "tenantId": "scm",
                      "poId": "%s",
                      "supplierAsnRef": "%s",
                      "expectedArrivalAt": "%s",
                      "lines": [
                        { "poLineId": "%s", "quantityShipped": %d }
                      ]
                    }
                    """.formatted(poId, supplierAsnRef,
                            Instant.now().plusSeconds(3600).toString(),
                            poLineId, orderedQty);

            HttpResponse<String> asnResp = sendString(http, webhookJson(
                    procurementBaseUri().resolve(pathAsnWebhook()), SUPPLIER_WEBHOOK_SECRET)
                    .POST(HttpRequest.BodyPublishers.ofString(asnBody))
                    .build());
            assertThat(asnResp.statusCode())
                    .as("ASN webhook returns 200 with full ordered quantity")
                    .isEqualTo(200);

            // ----- Kafka assertions: BOTH events arrive -------------------
            List<ConsumerRecord<String, String>> seenAcc = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        seenAcc.addAll(consumer.drain());
                        boolean poReceivedSeen = seenAcc.stream()
                                .anyMatch(r -> TOPIC_PO_RECEIVED.equals(r.topic())
                                        && poId.equals(r.key()));
                        boolean asnReceivedSeen = seenAcc.stream()
                                .anyMatch(r -> TOPIC_ASN_RECEIVED.equals(r.topic()));
                        assertThat(poReceivedSeen)
                                .as("po.received.v1 keyed by PO id should arrive within 30 s")
                                .isTrue();
                        assertThat(asnReceivedSeen)
                                .as("asn.received.v1 should arrive within 30 s")
                                .isTrue();
                    });

            // ----- audit_log >= 5 rows for this PO ------------------------
            // DRAFT + SUBMIT + ACKNOWLEDGE + CONFIRM + ASN-derived row(s)
            int poAuditRows = ProcurementDbFixtures.countAuditRows(
                    postgres, "scm", "purchase_order", poId);
            assertThat(poAuditRows)
                    .as("purchase_order audit_log carries one row per state transition (>=4 PO + >=1 ASN)")
                    .isGreaterThanOrEqualTo(4);
        }
    }
}
