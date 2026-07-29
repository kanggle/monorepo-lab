package com.kanggle.platformconsole.bff.integration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Failure-injection integration test for the per-leg circuit breaker
 * (TASK-PC-BE-015) — the ticket's F6 evidence.
 *
 * <p>A breaker exercised only through a test double proves the double. This
 * suite drives real repeated failures at a <b>booted</b> console-bff over HTTP,
 * so what is asserted is the <i>wiring</i>: the {@code @Component} adapter is
 * registered, injected into the composition use-cases, keyed
 * {@code (domain, route)}, and the resulting {@code CIRCUIT_OPEN} classification
 * reaches the wire envelope.
 *
 * <p>Scenarios (AC-2 / AC-3 / AC-4 / AC-6):
 * <ol>
 *   <li>Repeated {@code 503}s on the wms leg flip that card from
 *       {@code degraded/DOWNSTREAM_ERROR} to {@code degraded/CIRCUIT_OPEN}.</li>
 *   <li>Once open, a further dashboard load performs <b>zero</b> outbound
 *       requests against the wms stub (snapshot-and-diff on the stub's request
 *       counter) — fail-fast means no socket, not a fast failure after one.</li>
 *   <li>Sibling isolation: every other domain's card is still {@code ok} in the
 *       same envelope, and the same domain's <i>other</i> route
 *       ({@code operator-overview}) is untouched.</li>
 *   <li>{@code /actuator/prometheus} exposes
 *       {@code bff_fanout_errors_total{...,code="circuit_open"}} — the metric
 *       code the contract has always listed and nothing emitted until now.</li>
 * </ol>
 *
 * <p>The {@code domain-health} route is used as the driver because its legs are
 * credential-less, which keeps the fixture to "stub answers 503" with no token
 * plumbing in the way of the property under test.
 */
class CircuitBreakerIntegrationTest extends AbstractConsoleBffIntegrationTest {

    @SuppressWarnings("resource") static final MockWebServer GAP = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer WMS = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer SCM = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer FINANCE = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer ERP = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer ECOMMERCE = new MockWebServer();

    /**
     * {@code consolebff.resilience.circuit-breaker.minimum-number-of-calls} —
     * the smallest burst that can satisfy the COUNT_BASED window. Kept as a
     * constant rather than a literal so the loop below and the configured policy
     * cannot drift apart silently.
     */
    private static final int MIN_CALLS_TO_TRIP = 5;

    private static RSAKey rsaKey;
    private static String gapOidcJwt;

    @LocalServerPort
    int port;

    @BeforeAll
    static void startStubsAndJwt() throws Exception {
        GAP.start();
        WMS.start();
        SCM.start();
        FINANCE.start();
        ERP.start();
        ECOMMERCE.start();

        rsaKey = new RSAKeyGenerator(2048).keyID("test-key-it-cb").generate();
        publishJwks("{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}");

        JWSSigner signer = new RSASSASigner(rsaKey);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("http://test-issuer")
                .subject("op-user-it-cb")
                .audience("console-bff")
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                .claim("tenant_id", "iam")
                .build();
        SignedJWT signed = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key-it-cb").build(), claims);
        signed.sign(signer);
        gapOidcJwt = signed.serialize();
    }

    @AfterAll
    static void stopStubs() throws Exception {
        GAP.shutdown();
        WMS.shutdown();
        SCM.shutdown();
        FINANCE.shutdown();
        ERP.shutdown();
        ECOMMERCE.shutdown();
    }

    @DynamicPropertySource
    static void outboundBaseUrls(DynamicPropertyRegistry registry) {
        registry.add("consolebff.outbound.gap.base-url", () -> baseUrl(GAP));
        registry.add("consolebff.outbound.wms.base-url", () -> baseUrl(WMS));
        registry.add("consolebff.outbound.scm.base-url", () -> baseUrl(SCM));
        registry.add("consolebff.outbound.finance.base-url", () -> baseUrl(FINANCE));
        registry.add("consolebff.outbound.erp.base-url", () -> baseUrl(ERP));
        registry.add("consolebff.outbound.ecommerce.base-url", () -> baseUrl(ECOMMERCE));
    }

    private static String baseUrl(MockWebServer server) {
        try {
            if (server.getPort() <= 0) {
                server.start();
            }
        } catch (Exception ignored) { /* already started */ }
        return server.url("/").toString();
    }

    @BeforeEach
    void resetStubs() {
        for (MockWebServer s : all()) {
            s.setDispatcher(new QueueDispatcher());
            try {
                while (s.takeRequest(0, TimeUnit.MILLISECONDS) != null) {
                    // drain
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static MockWebServer[] all() {
        return new MockWebServer[]{GAP, WMS, SCM, FINANCE, ERP, ECOMMERCE};
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-2/3/4/6: repeated wms 503 opens (wms, domain-health) → card flips to CIRCUIT_OPEN, the next load fires ZERO wms requests, siblings stay ok, prometheus shows code=circuit_open")
    void repeated_failures_open_the_wms_leg_then_it_fails_fast_while_siblings_stay_ok() {
        // Healthy siblings for the whole scenario; wms answers 503 forever.
        respondAlways(GAP, 200, "{\"status\":\"UP\"}");
        respondAlways(SCM, 200, "{\"status\":\"UP\"}");
        respondAlways(FINANCE, 200, "{\"status\":\"UP\"}");
        respondAlways(ERP, 200, "{\"status\":\"UP\"}");
        respondAlways(ECOMMERCE, 200, "{\"status\":\"UP\"}");
        respondAlways(WMS, 503, "{}");

        // ── Phase 1: drive the breaker's window. Each dashboard load records
        // exactly ONE breaker call for (wms, domain-health) because the breaker
        // is the outermost decoration (the retry inside it is not a second call).
        for (int i = 0; i < MIN_CALLS_TO_TRIP; i++) {
            ResponseEntity<String> r = callHealth();
            assertThat(r.getStatusCode())
                    .as("composition must stay 200 while a leg is failing (D5.A; D5.B rejected)")
                    .isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).as("body:\n%s", r.getBody())
                    .contains("\"domain\":\"wms\"")
                    .contains("\"status\":\"degraded\"");
        }

        // ── Phase 2: the breaker is now OPEN. Snapshot the wms stub's counter
        // and load once more. MockWebServer.getRequestCount() is lifetime-
        // accumulated, so the assertion is a DELTA, not an absolute.
        int wmsBefore = WMS.getRequestCount();
        int scmBefore = SCM.getRequestCount();

        ResponseEntity<String> failFast = callHealth();
        String body = failFast.getBody();

        assertThat(failFast.getStatusCode())
                .as("still 200 — an open breaker degrades one card, never the dashboard; body:\n%s", body)
                .isEqualTo(HttpStatus.OK);

        // AC-6 / AC-3: the documented-but-dead classification is now on the wire.
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"wms\"")
                .contains("\"reason\":\"CIRCUIT_OPEN\"");

        // AC-3: fail-fast means NO outbound request at all.
        assertThat(WMS.getRequestCount() - wmsBefore)
                .as("an OPEN breaker must not open a socket — expected zero wms requests on this load")
                .isZero();

        // AC-4: the sibling legs are unaffected and still hit the network.
        assertThat(SCM.getRequestCount() - scmBefore)
                .as("the sibling domain must still be called")
                .isPositive();
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"scm\"")
                .contains("\"status\":\"UP\"");
        // No 'forbidden' anywhere on this route — § 2.4.9.2 invariant preserved.
        assertThat(body).as("body:\n%s", body).doesNotContain("\"forbidden\"");

        // AC-6: the metric code the contract lists but nothing emitted until now.
        ResponseEntity<String> prom = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/prometheus", String.class);
        assertThat(prom.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(prom.getBody()).as("prometheus:\n%s", prom.getBody())
                .contains("code=\"circuit_open\"");
        // The additive per-gate state gauge is exposed too.
        assertThat(prom.getBody()).as("prometheus:\n%s", prom.getBody())
                .contains("bff_circuit_breaker_state");
    }

    @Test
    @DisplayName("AC-4(b) wired: (wms, domain-health) OPEN leaves (wms, operator-overview) CLOSED — one dashboard's trip does not bleed into the other (contract § 2.4.9.2)")
    void open_health_breaker_does_not_bleed_into_the_overview_route() {
        respondAlways(GAP, 200, "{\"status\":\"UP\"}");
        respondAlways(SCM, 200, "{\"status\":\"UP\"}");
        respondAlways(FINANCE, 200, "{\"status\":\"UP\"}");
        respondAlways(ERP, 200, "{\"status\":\"UP\"}");
        respondAlways(ECOMMERCE, 200, "{\"status\":\"UP\"}");
        respondAlways(WMS, 503, "{}");

        for (int i = 0; i < MIN_CALLS_TO_TRIP; i++) {
            callHealth();
        }
        // Confirm the health-route breaker really is open before drawing any
        // conclusion from the overview route (never assert on an unverified
        // premise).
        assertThat(callHealth().getBody())
                .as("precondition: the domain-health wms leg must be fail-fast by now")
                .contains("\"reason\":\"CIRCUIT_OPEN\"");

        // Now the SAME domain on the OTHER route, with a healthy stub. A
        // domain-keyed-only breaker would blank this card too.
        respondAlways(GAP, 200, "{\"page\":{\"totalElements\":1}}");
        respondAlways(WMS, 200, "{\"snapshotTotal\":42}");
        respondAlways(SCM, 200, "{\"nodeCount\":3}");
        respondAlways(ERP, 200, "{\"meta\":{\"totalElements\":9}}");
        respondAlways(ECOMMERCE, 200, "{\"totalElements\":7}");

        int wmsBefore = WMS.getRequestCount();
        ResponseEntity<String> overview = restTemplate.exchange(
                "http://localhost:" + port + "/api/console/dashboards/operator-overview",
                HttpMethod.GET,
                new HttpEntity<>(overviewHeaders()),
                String.class);
        String body = overview.getBody();

        assertThat(overview.getStatusCode()).as("body:\n%s", body).isEqualTo(HttpStatus.OK);
        assertThat(body).as("the overview wms card must be ok — sibling breaker instances are "
                        + "independent (§ 2.4.9.2). body:\n%s", body)
                .contains("\"snapshotTotal\":42");
        assertThat(WMS.getRequestCount() - wmsBefore)
                .as("the operator-overview wms leg must still reach the network")
                .isPositive();
    }

    // ------------------------------------------------------------------

    private HttpHeaders healthHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + gapOidcJwt);
        h.set("X-Tenant-Id", "iam");
        return h;
    }

    private HttpHeaders overviewHeaders() {
        HttpHeaders h = healthHeaders();
        h.set("X-Operator-Token", "op-tok-cb");
        return h;
    }

    private ResponseEntity<String> callHealth() {
        return restTemplate.exchange(
                "http://localhost:" + port + "/api/console/dashboards/domain-health",
                HttpMethod.GET,
                new HttpEntity<>(healthHeaders()),
                String.class);
    }
}
