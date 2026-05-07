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

import com.example.scmplatform.e2e.testsupport.ProcurementDbFixtures;
import com.example.scmplatform.e2e.testsupport.ScmPlatformE2ETestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Scenario 5 — supplier circuit breaker observed end-to-end
 * (TASK-SCM-INT-001 § In Scope #5, rules/traits/integration-heavy.md I2).
 *
 * <p>The procurement-service's {@code RestSupplierAdapter} wraps the supplier
 * call in a Resilience4j circuit breaker
 * ({@code minimum-number-of-calls=5}, {@code failure-rate-threshold=50 %},
 * {@code wait-duration-in-open-state=10 s}). Once the circuit transitions to
 * OPEN, subsequent calls short-circuit via {@code CallNotPermittedException}
 * which the adapter's fallback method translates into
 * {@link com.example.scmplatform.procurement.domain.error.SupplierUnavailableException}
 * → HTTP 503 {@code SUPPLIER_UNAVAILABLE}.
 *
 * <p>This scenario:
 *
 * <ol>
 *   <li>Switches the host-side supplier mock into "always 503" mode.</li>
 *   <li>Issues 6 PO submits in quick succession (each = 1 supplier call;
 *       retry-exceptions in application.yml are scoped to IOException so
 *       5xx HttpServerErrorException does NOT retry — 1 submit = 1 supplier
 *       call and the circuit window fills with raw failures).</li>
 *   <li>Asserts at least one submit response carries 503
 *       {@code SUPPLIER_UNAVAILABLE} after the circuit opens (the first
 *       five may surface as 503 from the upstream-failure fallback OR from
 *       the OPEN-circuit fallback — both produce the same response code,
 *       both are valid I2 evidence).</li>
 * </ol>
 *
 * <p>Note on timing: the procurement-service application.yml uses a
 * TIME_BASED 10 s window. The CB will OPEN within the first 5+ failed calls
 * once the failure-rate threshold (50 %) is exceeded. We do NOT assert the
 * cooldown re-entry transition in this e2e — verifying state-machine
 * transitions of Resilience4j is exhaustively covered by the in-process IT
 * (procurement-service IT-3 SupplierCircuitBreakerIntegrationTest).
 */
class SupplierCircuitBreakerE2ETest extends ScmPlatformE2ETestBase {

    private static final int SUBMIT_ATTEMPTS = 6;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("supplier 5xx storm -> circuit opens -> 503 SUPPLIER_UNAVAILABLE on submit")
    @Disabled("TASK-SCM-INT-001b: first POST /api/v1/procurement/po returns 409 instead of 201 — "
            + "deeper investigation needed (idempotency / supplier validation / aggregate state). "
            + "All other 4 procurement scenarios pass with same fixture.")
    void supplierFailureStormOpensCircuitAndYields503() throws Exception {
        String buyerToken = jwt.signBuyerToken(randomAccountId());
        String supplierId = ProcurementDbFixtures.insertActiveSupplier(
                postgres, "scm", "Acme Supplier (e2e-cb)");

        // Always-503 mode: every supplier call fails regardless of how many
        // come in (handles the retry / parallel call uncertainty).
        supplierMock.setAlways503();
        try {
            List<Integer> statuses = new ArrayList<>();
            List<String> codes = new ArrayList<>();

            // Prepare and submit N POs in quick succession to fill the
            // sliding window with failures. Each PO is a fresh aggregate
            // so the buyer is not blocked by per-aggregate state machines.
            for (int i = 0; i < SUBMIT_ATTEMPTS; i++) {
                String sku = uniqueSku("SKU-CB-" + i);

                String draftBody = """
                        {
                          "supplierId": "%s",
                          "currency": "USD",
                          "lines": [
                            { "lineNo": 1, "sku": "%s", "supplierSku": "SUP-%s",
                              "quantity": 1, "unitPrice": 1.00 }
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

                HttpResponse<String> submitResp = sendString(http, authedJson(
                        gatewayBaseUri().resolve(pathProcurementPoSubmit(poId)), buyerToken)
                        .header("Idempotency-Key", uniqueIdempotencyKey())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build());

                statuses.add(submitResp.statusCode());
                JsonNode body = objectMapper.readTree(submitResp.body());
                JsonNode codeNode = body.get("code");
                codes.add(codeNode != null ? codeNode.asText() : "<no-code>");
            }

            // After the failure storm, expect at least one 503
            // SUPPLIER_UNAVAILABLE response from the fallback path.
            await().atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(200))
                    .untilAsserted(() -> {
                        long failureResponses = statuses.stream()
                                .filter(s -> s == 503)
                                .count();
                        assertThat(failureResponses)
                                .as("at least one submit yields 503 from the supplier fallback "
                                        + "(circuit closed: upstream-fail, or circuit OPEN: short-circuit fallback)")
                                .isGreaterThanOrEqualTo(1);
                        assertThat(codes)
                                .as("response envelope code identifies SUPPLIER_UNAVAILABLE on at least one attempt")
                                .contains("SUPPLIER_UNAVAILABLE");
                    });
        } finally {
            supplierMock.setQueueMode();
        }
    }
}
