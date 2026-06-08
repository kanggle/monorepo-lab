package com.example.ecommerce.e2e.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.ecommerce.e2e.testsupport.EcommerceFulfillmentE2EBase;
import com.example.ecommerce.e2e.testsupport.KafkaTestConsumer;
import com.example.ecommerce.e2e.testsupport.KafkaTestProducer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * TASK-MONO-195 (ADR-MONO-022 §D7 ④) — storefront purchase → connected warehouse
 * ships → order auto-SHIPPED, across the ecommerce side of the cross-project loop.
 *
 * <p>Real services: order-service + shipping-service. The wms boundary is
 * host-synthesised (the wms internal RECEIVED→SHIPPED saga is gated separately by
 * {@code FulfillmentRequestedConsumerIT}, TASK-BE-340/342) — the monorepo
 * cross-project idiom (scm {@code WmsInventoryAdjustedConsumedE2ETest} /
 * ADR-MONO-022 §D7 "Failure Scenario B").
 *
 * <p>Steps:
 * <ol>
 *   <li>POST /api/orders → order PENDING (REST).</li>
 *   <li>synthetic {@code product.product.stock-changed} (ORDER_RESERVED) →
 *       order CONFIRMED → {@code order.order.confirmed}.</li>
 *   <li>shipping-service creates Shipping PREPARING + emits the forward contract
 *       {@code ecommerce.fulfillment.requested.v1} — asserted on the broker
 *       (camelCase, customerPartnerCode=ECOMMERCE-STORE, shipTo populated,
 *       orderNo==orderId). This is the exact event the wms outbound-service
 *       consumes (AC-2 input contract).</li>
 *   <li>synthetic {@code wms.outbound.shipping.confirmed.v1} (orderNo==orderId) →
 *       shipping SHIPPED → {@code shipping.shipping.status-changed} → order
 *       SHIPPED.</li>
 *   <li>GET /api/orders/{id} → status SHIPPED (AC-1, correlated by orderNo).</li>
 * </ol>
 *
 * Tagged {@code full} → nightly + push-to-main only (ADR-MONO-010/011); never the
 * fast PR lane (cross-project boot cost).
 */
@Tag("full")
class FulfillmentLoopE2ETest extends EcommerceFulfillmentE2EBase {

    private static final String USER_ID = "user-e2e-1";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("storefront purchase → confirmed → fulfillment requested → wms ships → order SHIPPED")
    void fullFulfillmentLoopShipsTheOrder() throws Exception {
        // ----- Step 1: place an order via REST -> PENDING --------------------
        String orderBody = """
                {
                  "items": [
                    {
                      "productId": "PROD-1",
                      "variantId": "SKU-APPLE-001",
                      "productName": "Apple",
                      "optionName": "Red",
                      "quantity": 2,
                      "unitPrice": 1500
                    }
                  ],
                  "shippingAddress": {
                    "recipient": "홍길동",
                    "phone": "010-1234-5678",
                    "zipCode": "06236",
                    "address1": "서울특별시 강남구 테헤란로 1",
                    "address2": "10층"
                  }
                }
                """;
        HttpResponse<String> placed = send(HttpRequest.newBuilder()
                .uri(orderBaseUri().resolve("/api/orders"))
                .header("X-User-Id", USER_ID)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(orderBody))
                .build());
        assertThat(placed.statusCode()).as("POST /api/orders -> 201").isEqualTo(201);
        String orderId = mapper.readTree(placed.body()).get("orderId").asText();
        assertThat(orderId).as("orderId returned").isNotBlank();
        log.info("[e2e] placed orderId={}", orderId);

        try (KafkaTestProducer producer = new KafkaTestProducer(kafkaBootstrapForHost());
             KafkaTestConsumer fulfillmentConsumer =
                     new KafkaTestConsumer(kafkaBootstrapForHost(), TOPIC_FULFILLMENT_REQUESTED)) {

            // ----- Step 2: synthetic stock-changed -> order CONFIRMED --------
            producer.publishStockChangedOrderReserved(orderId, "PROD-1", "SKU-APPLE-001");

            await().atMost(Duration.ofSeconds(45))
                    .pollInterval(Duration.ofMillis(750))
                    .untilAsserted(() -> assertThat(orderStatus(orderId))
                            .as("order reaches CONFIRMED after stock-changed (ORDER_RESERVED)")
                            .isEqualTo("CONFIRMED"));
            log.info("[e2e] order CONFIRMED");

            // ----- Step 3: assert the forward contract event on the broker ----
            // (AC-2 input: this is exactly what the wms outbound-service consumes
            // to create an order with source=FULFILLMENT_ECOMMERCE + shipTo.)
            JsonNode forward = fulfillmentConsumer.pollFor(
                    n -> n.path("payload").path("orderNo").asText("").equals(orderId),
                    Duration.ofSeconds(30));
            assertThat(forward)
                    .as("ecommerce.fulfillment.requested.v1 emitted for orderNo=%s", orderId)
                    .isNotNull();
            JsonNode fwPayload = forward.get("payload");
            assertThat(forward.get("eventType").asText()).isEqualTo("ecommerce.fulfillment.requested");
            assertThat(fwPayload.get("customerPartnerCode").asText()).isEqualTo("ECOMMERCE-STORE");
            assertThat(fwPayload.get("warehouseCode").asText()).isEqualTo("WH-MAIN");
            assertThat(fwPayload.path("shipTo").path("recipientName").asText())
                    .as("shipTo round-trips the order recipient (AC-2 input)")
                    .isEqualTo("홍길동");
            assertThat(fwPayload.path("lines").isArray() && fwPayload.path("lines").size() >= 1)
                    .as("forward event carries at least one line").isTrue();
            log.info("[e2e] forward fulfillment.requested asserted (camelCase, ECOMMERCE-STORE, shipTo)");

            // ----- Step 4: synthetic wms shipping.confirmed -> return leg -----
            producer.publishWmsShippingConfirmed(orderId, "SHP-E2E-0001", "CJ");

            // ----- Step 5: order auto-SHIPPED (AC-1) -------------------------
            await().atMost(Duration.ofSeconds(45))
                    .pollInterval(Duration.ofMillis(750))
                    .untilAsserted(() -> assertThat(orderStatus(orderId))
                            .as("order auto-SHIPPED after wms shipping.confirmed (orderNo correlation)")
                            .isEqualTo("SHIPPED"));
            log.info("[e2e] order SHIPPED — full loop closed");
        }
    }

    private String orderStatus(String orderId) throws Exception {
        HttpResponse<String> resp = send(HttpRequest.newBuilder()
                .uri(orderBaseUri().resolve("/api/orders/" + orderId))
                .header("X-User-Id", USER_ID)
                .GET().build());
        if (resp.statusCode() != 200) {
            return "HTTP_" + resp.statusCode();
        }
        return mapper.readTree(resp.body()).get("status").asText();
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
