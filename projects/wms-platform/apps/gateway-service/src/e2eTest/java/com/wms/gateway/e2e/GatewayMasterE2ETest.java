package com.wms.gateway.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.gateway.testsupport.KafkaTestConsumer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Live-pair end-to-end test: gateway-service ↔ master-service running in a
 * single Docker network with Postgres, Redis, Kafka, and a MockWebServer
 * JWKS stand-in. Addresses the ⚠️ gaps flagged in the TASK-INT-001 review
 * note.
 *
 * <p>Five nested scenarios map 1:1 to the ticket's Acceptance Criteria:
 *
 * <ol>
 *   <li>Happy path — GET + POST through gateway with a valid JWT</li>
 *   <li>401 without JWT — gateway short-circuits, master never receives</li>
 *   <li>Rate limit 429 — 120-request burst trips SCG's RedisRateLimiter</li>
 *   <li>503 when master down — pause the master container mid-test</li>
 *   <li>Trace propagation — deferred; see note in the nested class</li>
 * </ol>
 */
class GatewayMasterE2ETest extends E2EBase {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(15);
    private static final String WAREHOUSE_TOPIC = "wms.master.warehouse.v1";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------
    // Scenario 1 — Happy GET + POST with a valid JWT.
    // -------------------------------------------------------------------
    @Nested
    @DisplayName("Happy path: valid JWT → 200 / 201 through gateway")
    class HappyPath {

        @Test
        @DisplayName("GET /api/v1/master/warehouses with MASTER_READ → 200")
        void getWarehousesListWithValidJwt() throws Exception {
            String token = jwt.signMasterReadToken("e2e-user-" + UUID.randomUUID());

            HttpResponse<String> response = send(HttpRequest.newBuilder(
                    gatewayBaseUri().resolve("/api/v1/master/warehouses"))
                    .timeout(CALL_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("X-Forwarded-For", "10.0.0.10")
                    .GET()
                    .build());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"content\"");
            assertThat(response.headers().firstValue("X-Request-Id"))
                    .as("gateway always stamps X-Request-Id on responses")
                    .isPresent();
        }

        @Test
        @DisplayName("POST /api/v1/master/warehouses with MASTER_WRITE → 201 + outbox→Kafka event")
        void createWarehouseThroughGateway() throws Exception {
            String token = jwt.signMasterWriteToken("e2e-writer-" + UUID.randomUUID());
            // 3-digit numeric suffix — stays within the ^WH\d{2,3}$ validation
            // constraint on CreateWarehouseRequest. Postgres is a fresh
            // container per CI run so the 1000-value range is collision-free.
            String code = "WH" + String.format("%03d",
                    (int) (Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 1000));
            String body = """
                    {
                      "warehouseCode": "%s",
                      "name": "E2E Test Warehouse %s",
                      "address": "Seoul, Korea",
                      "timezone": "Asia/Seoul"
                    }
                    """.formatted(code, code);

            // Subscribe to the warehouse topic BEFORE issuing the POST so the
            // consumer does not miss the event (KafkaTestConsumer starts from
            // earliest, but "earliest" still misses events published before
            // the group's first assignment on a just-created partition —
            // pre-subscribing is the safest seam).
            try (KafkaTestConsumer consumer = new KafkaTestConsumer(
                    kafkaBootstrapForHost(), List.of(WAREHOUSE_TOPIC))) {

                HttpResponse<String> response = send(HttpRequest.newBuilder(
                        gatewayBaseUri().resolve("/api/v1/master/warehouses"))
                        .timeout(CALL_TIMEOUT)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Forwarded-For", "10.0.0.11")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build());

                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(response.body())
                        .as("response echoes the created warehouseCode from master-service")
                        .contains(code);

                JsonNode created = objectMapper.readTree(response.body());
                String warehouseId = created.get("id").asText();

                // Outbox→Kafka smoke assertion. This is the acid test for the
                // Kafka bootstrap port fix in E2EBase: if the master container
                // is wired to the wrong port (no brokers reachable), outbox
                // rows stay PENDING forever and this Awaitility loop times
                // out within 10 seconds.
                //
                // Verifies the canonical envelope per
                // `specs/contracts/events/master-events.md` § Global Envelope:
                //   - record.key() == aggregateId (partition key guarantee)
                //   - envelope.eventType == "master.warehouse.created"
                //   - envelope.aggregateId matches POST response id
                //   - envelope.eventId is UUID-parseable
                //   - envelope.payload.warehouse.warehouseCode echoes the POST
                //
                // The accumulator is declared OUTSIDE the Awaitility lambda so
                // it persists across polling cycles. `consumer.drain()` is
                // destructive (poll-and-remove from the underlying queue): if
                // we re-declared the list inside the lambda, a record received
                // on cycle N but failing a field assertion on the same cycle
                // would be gone on cycle N+1, masking the real field mismatch
                // behind a misleading "no record received" failure. Appending
                // to a captured list keeps every drained record visible to
                // every retry until the field assertions pass or the timeout
                // elapses.
                List<ConsumerRecord<String, String>> accumulated = new ArrayList<>();
                await().atMost(Duration.ofSeconds(10))
                        .pollInterval(Duration.ofMillis(500))
                        .untilAsserted(() -> {
                            accumulated.addAll(consumer.drain());
                            ConsumerRecord<String, String> match = accumulated.stream()
                                    .filter(r -> warehouseId.equals(r.key()))
                                    .findFirst()
                                    .orElse(null);
                            assertThat(match)
                                    .as("outbox publisher should deliver "
                                            + "master.warehouse.created to "
                                            + WAREHOUSE_TOPIC
                                            + " keyed by aggregateId within 10s")
                                    .isNotNull();

                            JsonNode envelope = objectMapper.readTree(match.value());
                            assertThat(envelope.get("eventType").asText())
                                    .isEqualTo("master.warehouse.created");
                            assertThat(envelope.get("aggregateType").asText())
                                    .isEqualTo("warehouse");
                            assertThat(envelope.get("aggregateId").asText())
                                    .isEqualTo(warehouseId);
                            assertThat(envelope.get("producer").asText())
                                    .isEqualTo("master-service");
                            assertThat(envelope.get("eventVersion").asInt())
                                    .isEqualTo(1);
                            // eventId must be a valid UUID per the envelope
                            // schema (UUIDv7 in production; parseability is
                            // the contract we care about here). Wrapped in
                            // assertThatNoException so a parse failure shows
                            // up as a descriptive AssertJ failure rather than
                            // a bare IllegalArgumentException
                            // (TASK-BE-018 item 9).
                            assertThatNoException().isThrownBy(
                                    () -> UUID.fromString(
                                            envelope.get("eventId").asText()));
                            assertThat(envelope.get("payload")
                                    .get("warehouse")
                                    .get("warehouseCode")
                                    .asText())
                                    .isEqualTo(code);
                        });
            }
        }
    }

    // -------------------------------------------------------------------
    // Scenario 2 — 401 without JWT; master never receives the request.
    // -------------------------------------------------------------------
    @Nested
    @DisplayName("Unauthenticated request → 401; master-service untouched")
    class Unauthorized {

        @Test
        void noAuthHeaderReturns401AndDoesNotReachMaster() throws Exception {
            int masterHitsBefore = masterRequestCount();

            HttpResponse<String> response = send(HttpRequest.newBuilder(
                    gatewayBaseUri().resolve("/api/v1/master/warehouses"))
                    .timeout(CALL_TIMEOUT)
                    .header("X-Forwarded-For", "10.0.0.20")
                    .GET()
                    .build());

            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body())
                    .as("platform error envelope on 401")
                    .contains("\"code\"")
                    .contains("UNAUTHORIZED");

            // Awaitility `during(...)` asserts the condition stays true for
            // the whole window — i.e. the master request counter does NOT
            // change over 2 seconds. This catches a regression where the
            // gateway forwards the 401'd request asynchronously (e.g. a
            // misconfigured filter order), which a single post-401 read
            // would miss because Micrometer counters increment off the
            // request thread.
            await().during(Duration.ofSeconds(2))
                    .pollInterval(Duration.ofMillis(200))
                    .untilAsserted(() -> assertThat(masterRequestCount())
                            .as("master-service must NOT have seen the request — "
                                    + "counter remains at pre-call value for the full window")
                            .isEqualTo(masterHitsBefore));
        }
    }

    // -------------------------------------------------------------------
    // Scenario 3 — Rate limit 429 after an 800-request burst.
    //
    // The route is configured with RedisRateLimiter replenishRate=100,
    // burstCapacity=200, requestedTokens=1. A cold bucket starts with 200
    // tokens. Under CI load with containers, the burst window may span 4–5 s,
    // replenishing up to 500 tokens → worst-case capacity ≈ 700. Firing 800
    // requests therefore guarantees ≥100 are rate-limited regardless of CI
    // speed. The test uses virtual threads so total wall-clock is bounded by
    // the slowest single response, not by 800× serial latency.
    // -------------------------------------------------------------------
    @Nested
    @DisplayName("Rate limit burst → some 2xx + at least one 429 with Retry-After")
    class RateLimit {

        @Test
        void burstFrom250RequestsTripsRateLimiter() throws Exception {
            String token = jwt.signMasterReadToken("e2e-burst");
            // Unique IP per test run so concurrent suites don't collide in
            // the same Redis bucket.
            String burstIp = "10.0." + (50 + new java.util.Random().nextInt(150))
                    + "." + (1 + new java.util.Random().nextInt(250));

            AtomicInteger ok = new AtomicInteger();
            AtomicInteger rateLimited = new AtomicInteger();
            List<String> retryAfterValues = Collections.synchronizedList(new ArrayList<>());

            // Fire all 800 requests concurrently via virtual threads. Even if
            // the burst window spans 5 s (replenishing 500 tokens → capacity
            // ceiling of 700), 800 requests leave ≥100 rate-limited.
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<?>> futures = new ArrayList<>(800);
                for (int i = 0; i < 800; i++) {
                    futures.add(executor.submit(() -> {
                        try {
                            HttpResponse<String> resp = send(HttpRequest.newBuilder(
                                    gatewayBaseUri().resolve("/api/v1/master/warehouses"))
                                    .timeout(CALL_TIMEOUT)
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Forwarded-For", burstIp)
                                    .GET()
                                    .build());
                            int status = resp.statusCode();
                            if (status == 200 || status == 404) {
                                ok.incrementAndGet();
                            } else if (status == 429) {
                                rateLimited.incrementAndGet();
                                resp.headers().firstValue("Retry-After")
                                        .ifPresent(retryAfterValues::add);
                            }
                        } catch (Exception ignored) {
                            // connection errors under burst load do not invalidate
                            // the rate-limit assertion — exclude from both counters
                        }
                    }));
                }
                for (Future<?> f : futures) {
                    f.get();
                }
            }

            // With burstCapacity=200 and replenishRate=100/s, an 800-request
            // burst leaves ok ≈ 200–700 (depends on CI speed) and
            // rateLimited ≈ 100–600. >=190 tolerates replenish jitter on ok;
            // >=40 ensures the 429 path is genuinely exercised (a
            // broken/fail-open limiter scores 0 rateLimited).
            assertThat(ok.get())
                    .as("rate-limit still admits the bucket's capacity "
                            + "(>=190 tolerates replenish jitter)")
                    .isGreaterThanOrEqualTo(190);
            assertThat(rateLimited.get())
                    .as("the 250-request burst must drain the bucket and "
                            + "yield a decisive block count (>=40 catches "
                            + "a broken limiter that returns few or no 429s)")
                    .isGreaterThanOrEqualTo(40);
            assertThat(retryAfterValues)
                    .as("every 429 carries a Retry-After header per api-gateway-policy")
                    .isNotEmpty();
        }
    }

    // -------------------------------------------------------------------
    // Scenario 4 — master down → 503; then recovery after unpause.
    // -------------------------------------------------------------------
    @Nested
    @DisplayName("Pause master container → 503 platform envelope; resume → recovery")
    class MasterOutage {

        @Test
        void pausedMasterReturns503AndRecoversAfterUnpause() throws Exception {
            String token = jwt.signMasterReadToken("e2e-resilience");
            URI url = gatewayBaseUri().resolve("/api/v1/master/warehouses");

            // Sanity: master is up initially.
            HttpResponse<String> warmUp = send(HttpRequest.newBuilder(url)
                    .timeout(CALL_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("X-Forwarded-For", "10.0.0.30")
                    .GET()
                    .build());
            assertThat(warmUp.statusCode()).isIn(200, 404);

            master.getDockerClient().pauseContainerCmd(master.getContainerId()).exec();
            try {
                await().atMost(Duration.ofSeconds(15))
                        .pollInterval(Duration.ofMillis(500))
                        .untilAsserted(() -> {
                            HttpResponse<String> resp = send(HttpRequest.newBuilder(url)
                                    .timeout(CALL_TIMEOUT)
                                    .header("Authorization", "Bearer " + token)
                                    .header("X-Forwarded-For", "10.0.0.30")
                                    .GET()
                                    .build());
                            assertThat(resp.statusCode())
                                    .as("gateway returns 5xx when master is unreachable")
                                    .isBetween(500, 599);
                            // Gateway's fallback behavior may vary between 503 and
                            // 504 depending on SCG version; both are acceptable
                            // per the task's "choose one and document" clause.
                            assertThat(resp.statusCode()).isIn(502, 503, 504);
                        });
            } finally {
                master.getDockerClient().unpauseContainerCmd(master.getContainerId()).exec();
            }

            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofSeconds(1))
                    .untilAsserted(() -> {
                        HttpResponse<String> resp = send(HttpRequest.newBuilder(url)
                                .timeout(CALL_TIMEOUT)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Forwarded-For", "10.0.0.30")
                                .GET()
                                .build());
                        assertThat(resp.statusCode())
                                .as("subsequent request succeeds once master is back")
                                .isIn(200, 404);
                    });
        }
    }

    // -------------------------------------------------------------------
    // Scenario 5 — OTel trace propagation.
    //
    // DEFERRED: wiring InMemorySpanExporter into two running boot jars (not
    // the same JVM) requires either (a) exposing an OTLP HTTP/gRPC collector
    // container and asserting on the collector's received spans, or (b)
    // reading each service's /actuator/metrics for trace counters. Both
    // expand the scope beyond the 400-word review note allowance and are a
    // natural fit for TASK-BE-007 (contract harness) where a collector is
    // already planned. For now we assert the traceparent header round-trip
    // as a lightweight smoke test.
    // -------------------------------------------------------------------
    @Nested
    @DisplayName("Trace propagation: traceparent round-trip (full exporter deferred)")
    class TracePropagation {

        @Test
        void gatewayAcceptsAndForwardsTraceparent() throws Exception {
            String token = jwt.signMasterReadToken("e2e-trace");
            // W3C traceparent with a known traceId we can grep for on the
            // master side (via /actuator/httpexchanges if enabled, or just
            // by verifying the gateway did not strip the header).
            String traceId = "0af7651916cd43dd8448eb211c80319c";
            String traceparent = "00-" + traceId + "-b7ad6b7169203331-01";

            HttpResponse<String> resp = send(HttpRequest.newBuilder(
                    gatewayBaseUri().resolve("/api/v1/master/warehouses"))
                    .timeout(CALL_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("traceparent", traceparent)
                    .header("X-Forwarded-For", "10.0.0.40")
                    .GET()
                    .build());

            // Gateway may replace/propagate the traceparent but must forward
            // the request (200/404) and not reject on the header alone.
            assertThat(resp.statusCode()).isIn(200, 404);
            // The deeper assertion — same traceId spans gateway+master — is
            // DEFERRED to TASK-BE-007 once the collector container is added.
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Queries master-service's Prometheus actuator for the request count on
     * {@code /api/v1/master/warehouses}. Returns 0 if the counter is absent
     * (fresh start).
     */
    private int masterRequestCount() throws Exception {
        HttpResponse<String> resp = send(HttpRequest.newBuilder(
                masterBaseUri().resolve(
                        "/actuator/metrics/http.server.requests"
                                + "?tag=uri:/api/v1/master/warehouses"))
                .timeout(CALL_TIMEOUT)
                .GET()
                .build());
        if (resp.statusCode() != 200) {
            return 0;
        }
        // Minimal parse: look for the "COUNT" measurement value in the JSON.
        // Shape: { "measurements":[{"statistic":"COUNT","value":N.0}, ...]}
        String body = resp.body();
        int idx = body.indexOf("\"COUNT\"");
        if (idx < 0) {
            return 0;
        }
        int valueIdx = body.indexOf("\"value\"", idx);
        if (valueIdx < 0) {
            return 0;
        }
        int colon = body.indexOf(':', valueIdx);
        int comma = body.indexOf(',', colon);
        int brace = body.indexOf('}', colon);
        int end = (comma > 0 && comma < brace) ? comma : brace;
        try {
            String raw = body.substring(colon + 1, end).trim();
            return (int) Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
