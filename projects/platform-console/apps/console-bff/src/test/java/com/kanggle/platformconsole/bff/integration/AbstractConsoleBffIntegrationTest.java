package com.kanggle.platformconsole.bff.integration;

import com.example.testsupport.integration.DockerAvailableCondition;
import com.kanggle.platformconsole.bff.adapter.outbound.resilience.Resilience4jLegResilienceAdapter;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Base for console-bff integration tests.
 *
 * <p>console-bff is stateless (no DB / Kafka / Redis). Testcontainers is
 * included as baseline harness per AC-12 — this base class starts only a
 * MockWebServer for the GAP JWKS stub. The Spring context is fully booted
 * ({@code @SpringBootTest(webEnvironment = RANDOM_PORT)}).
 *
 * <p>AC-12 requirements:
 * <ol>
 *   <li>GAP JWKS stubbed via MockWebServer (WireMock alternative, same pattern
 *       as erp/finance precedent).</li>
 *   <li>{@code GET /actuator/health} returns 200.</li>
 *   <li>Per-domain {@code CredentialSelectionPort} 5-row dispatch dry-run.</li>
 *   <li>{@code GET /actuator/prometheus} exposes the 3 mandatory metric names.</li>
 *   <li>{@code OperatorCredentialContext} reads {@code X-Operator-Token} and
 *       {@code X-Tenant-Id} headers correctly.</li>
 * </ol>
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractConsoleBffIntegrationTest {

    /** MockWebServer that stubs the GAP JWKS endpoint. */
    @SuppressWarnings("resource")
    protected static final MockWebServer JWKS_SERVER = new MockWebServer();

    private static volatile String jwksBody = "{\"keys\":[]}";

    protected static void publishJwks(String jwksJson) {
        jwksBody = jwksJson;
    }

    static {
        JWKS_SERVER.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(jwksBody);
            }
        });
        try {
            JWKS_SERVER.start();
        } catch (IOException e) {
            throw new IllegalStateException("JWKS MockWebServer start failed", e);
        }
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        // Wire JWKS stub URL into Spring Security OAuth2 Resource Server config.
        String jwksUri = JWKS_SERVER.url("/oauth2/jwks").toString();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwksUri);
        // Use a test issuer that we control (matches published JWKS tokens).
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://test-issuer");
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    /**
     * The live per-leg resilience gate (TASK-PC-BE-015). Autowired so every
     * integration test can re-CLOSE the breakers between methods.
     */
    @Autowired
    protected Resilience4jLegResilienceAdapter legResilience;

    /**
     * Circuit-breaker state is <b>process-global</b> and outlives a test method:
     * the adapter is a singleton in the shared Spring context. Without this, a
     * test that drives 503s leaves a breaker OPEN and an unrelated later test
     * silently runs on the fail-fast path — a confident wrong assertion whose
     * appearance depends on JUnit's (unspecified) method order. Resetting in the
     * base class means no subclass has to remember.
     *
     * <p>Runs before each subclass {@code @BeforeEach} (JUnit orders superclass
     * lifecycle callbacks first).
     */
    @BeforeEach
    void resetCircuitBreakers() {
        legResilience.reset();
    }

    /**
     * Stubs {@code server} to answer <b>every</b> request with the same response,
     * for as long as the test runs.
     *
     * <p>Failure scenarios must use this rather than a one-shot {@code enqueue}:
     * a failing leg is now <b>retried once</b> (TASK-PC-BE-015), and
     * {@link okhttp3.mockwebserver.QueueDispatcher} <i>blocks</i> when its queue
     * is empty — so a single enqueued {@code 503} would make the retry attempt
     * hang until the 2s read timeout and reclassify {@code DOWNSTREAM_ERROR} into
     * {@code TIMEOUT}. Answering repeatedly makes the assertion
     * retry-count-independent, which is what we actually want to assert.
     *
     * <p>Note {@code MockWebServer.enqueue(...)} casts the dispatcher to
     * {@code QueueDispatcher}, so a server switched to this dispatcher must not
     * also be {@code enqueue}d within the same test.
     */
    protected static void respondAlways(MockWebServer server, int status, String json) {
        respondAlways(server, status, json, 0L);
    }

    /**
     * {@link #respondAlways(MockWebServer, int, String)} with a headers delay, for
     * legs that must exceed the 2s per-leg read timeout.
     *
     * <p>A delayed <b>one-shot</b> {@code enqueue} is specifically hazardous now:
     * the timed-out attempt is retried, the retry finds an empty
     * {@link okhttp3.mockwebserver.QueueDispatcher} and parks a MockWebServer
     * worker thread on {@code responseQueue.take()} <i>forever</i>. That thread
     * never returns, so {@code @AfterAll}'s {@code shutdown()} fails the whole
     * class with {@code IOException: Gave up waiting for queue to shut down} —
     * a green suite plus a red class-level {@code executionError}. Always
     * answering removes the park.
     */
    protected static void respondAlways(MockWebServer server, int status, String json,
                                        long headersDelayMs) {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                MockResponse response = new MockResponse()
                        .setResponseCode(status)
                        .setHeader("Content-Type", "application/json")
                        .setBody(json);
                if (headersDelayMs > 0) {
                    response.setHeadersDelay(headersDelayMs, TimeUnit.MILLISECONDS);
                }
                return response;
            }
        });
    }
}
