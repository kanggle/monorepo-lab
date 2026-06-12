package com.kanggle.platformconsole.bff.integration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the Domain Health Overview composition surface
 * (§ 2.4.9.2 / TASK-PC-FE-013).
 *
 * <p>Extends {@link AbstractConsoleBffIntegrationTest} (reuses JWKS_SERVER +
 * spring.security.oauth2.resourceserver wiring). Adds 6 MockWebServer instances
 * stubbing the 6 backend domains' public {@code /actuator/health} endpoints
 * (ecommerce 6th leg added by TASK-MONO-241).
 *
 * <p>Coverage:
 * <ul>
 *   <li>Happy path 5-card all-UP envelope with credential-LESS dispatch
 *       assertions (no {@code Authorization} / {@code X-Tenant-Id} /
 *       {@code X-Operator-Token} on any outbound leg).</li>
 *   <li>Per-leg degrade (wms 503 → DOWNSTREAM_ERROR).</li>
 *   <li>Per-leg downstream 401 → degraded (NOT cross-leg 401 collapse —
 *       distinguishing this route from § 2.4.9.1).</li>
 *   <li>All-down 5x 503 → still 200 envelope with 5 degraded cards.</li>
 *   <li>Inbound missing X-Tenant-Id → 400 NO_ACTIVE_TENANT (for log MDC /
 *       audit traceability; no outbound call fires).</li>
 *   <li>Inbound missing Authorization → 401 (Spring Security entry point;
 *       no outbound).</li>
 * </ul>
 */
class DomainHealthIntegrationTest extends AbstractConsoleBffIntegrationTest {

    @SuppressWarnings("resource") static final MockWebServer GAP = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer WMS = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer SCM = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer FINANCE = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer ERP = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer ECOMMERCE = new MockWebServer();

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

        rsaKey = new RSAKeyGenerator(2048).keyID("test-key-it-health").generate();
        String publicJwksJson = "{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}";
        publishJwks(publicJwksJson);

        JWSSigner signer = new RSASSASigner(rsaKey);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("http://test-issuer")
                .subject("op-user-it-health")
                .audience("console-bff")
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                .claim("tenant_id", "iam")
                .build();

        SignedJWT signed = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key-it-health").build(),
                claims);
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
        } catch (Exception ignored) { /* start may throw if already started */ }
        return server.url("/").toString();
    }

    @BeforeEach
    void resetStubs() {
        resetMockServer(GAP);
        resetMockServer(WMS);
        resetMockServer(SCM);
        resetMockServer(FINANCE);
        resetMockServer(ERP);
        resetMockServer(ECOMMERCE);
    }

    private static void resetMockServer(MockWebServer server) {
        server.setDispatcher(new okhttp3.mockwebserver.QueueDispatcher());
        try {
            while (true) {
                RecordedRequest r = server.takeRequest(0, TimeUnit.MILLISECONDS);
                if (r == null) break;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + gapOidcJwt);
        // X-Tenant-Id required for log MDC / audit traceability — NOT forwarded
        // to outbound legs. Verified by takeRequest below.
        h.set("X-Tenant-Id", "iam");
        // No X-Operator-Token — this route does NOT consume it.
        return h;
    }

    private ResponseEntity<String> callHealth(HttpHeaders headers) {
        return restTemplate.exchange(
                "http://localhost:" + port + "/api/console/dashboards/domain-health",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }

    private static void respond(MockWebServer server, int status, String json) {
        server.enqueue(new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(json));
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("happy_6_cards_all_UP: credential-LESS dispatch verified (no Authorization / X-Tenant-Id / X-Operator-Token on any outbound leg)")
    void happy_6_cards_all_UP_credential_less_dispatch_verified() throws Exception {
        respond(GAP, 200, "{\"status\":\"UP\"}");
        respond(WMS, 200, "{\"status\":\"UP\"}");
        respond(SCM, 200, "{\"status\":\"UP\"}");
        respond(FINANCE, 200, "{\"status\":\"UP\"}");
        respond(ERP, 200, "{\"status\":\"UP\"}");
        respond(ECOMMERCE, 200, "{\"status\":\"UP\"}");

        ResponseEntity<String> response = callHealth(authHeaders());
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("non-200 body:\n%s", body)
                .isEqualTo(HttpStatus.OK);
        // Envelope shape (§ 2.4.9.2).
        assertThat(body).as("body:\n%s", body).contains("\"asOf\"").contains("\"cards\"");
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"iam\"")
                .contains("\"domain\":\"wms\"")
                .contains("\"domain\":\"scm\"")
                .contains("\"domain\":\"finance\"")
                .contains("\"domain\":\"erp\"")
                .contains("\"domain\":\"ecommerce\"")
                .contains("\"status\":\"UP\"");
        // No 'forbidden' anywhere on this route — § 2.4.9.2 invariant.
        assertThat(body).as("body:\n%s", body).doesNotContain("\"forbidden\"");

        // ───────────────────────────────────────────────────────────────
        // The headline invariant of § 2.4.9.2: every outbound leg is
        // credential-LESS. No Authorization, no X-Tenant-Id, no
        // X-Operator-Token. The D4 sealed-switch is NOT invoked.
        // Path must be /actuator/health on every leg.
        // ───────────────────────────────────────────────────────────────
        for (MockWebServer s : new MockWebServer[]{GAP, WMS, SCM, FINANCE, ERP, ECOMMERCE}) {
            RecordedRequest r = s.takeRequest(2, TimeUnit.SECONDS);
            assertThat(r).as("expected outbound on stub %s", s.getPort()).isNotNull();
            assertThat(r.getPath())
                    .as("each leg must hit /actuator/health")
                    .isEqualTo("/actuator/health");
            assertThat(r.getHeader(HttpHeaders.AUTHORIZATION))
                    .as("actuator leg must NOT carry an Authorization header (D4 scope clarification)")
                    .isNull();
            assertThat(r.getHeader("X-Tenant-Id"))
                    .as("actuator leg must NOT carry X-Tenant-Id (not tenant-scoped)")
                    .isNull();
            assertThat(r.getHeader("X-Operator-Token"))
                    .as("actuator leg must NOT carry X-Operator-Token (not consumed by this route)")
                    .isNull();
        }
    }

    @Test
    @DisplayName("data_status_DOWN_propagated: producer self-reports DOWN → ok at composition layer with data.status=DOWN")
    void data_status_DOWN_propagated() {
        // Producer honestly reports itself DOWN — leg is still 'ok' at the
        // composition layer (the BFF reached the producer); UI distinguishes
        // visually between 'ok+UP' (green) and 'ok+DOWN' (red).
        respond(GAP, 200, "{\"status\":\"DOWN\"}");
        respond(WMS, 200, "{\"status\":\"UP\"}");
        respond(SCM, 200, "{\"status\":\"UP\"}");
        respond(FINANCE, 200, "{\"status\":\"UP\"}");
        respond(ERP, 200, "{\"status\":\"UP\"}");
        respond(ECOMMERCE, 200, "{\"status\":\"UP\"}");

        ResponseEntity<String> response = callHealth(authHeaders());
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("non-200 body:\n%s", body)
                .isEqualTo(HttpStatus.OK);
        // gap card ok + data.status=DOWN; NOT degraded.
        assertThat(body).as("body:\n%s", body).contains("\"domain\":\"iam\"");
        assertThat(body).as("body:\n%s", body).contains("\"status\":\"DOWN\"");
        // wms still ok + UP.
        assertThat(body).as("body:\n%s", body).contains("\"status\":\"UP\"");
    }

    // ------------------------------------------------------------------
    // Per-leg degrade
    // ------------------------------------------------------------------

    @Test
    @DisplayName("per_leg_degrade_wms_503: 200 envelope; wms card degraded/DOWNSTREAM_ERROR")
    void per_leg_degrade_wms_503() {
        respond(GAP, 200, "{\"status\":\"UP\"}");
        respond(WMS, 503, "{}");
        respond(SCM, 200, "{\"status\":\"UP\"}");
        respond(FINANCE, 200, "{\"status\":\"UP\"}");
        respond(ERP, 200, "{\"status\":\"UP\"}");
        respond(ECOMMERCE, 200, "{\"status\":\"UP\"}");

        ResponseEntity<String> response = callHealth(authHeaders());
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("non-200 body:\n%s", body)
                .isEqualTo(HttpStatus.OK);
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"wms\"")
                .contains("\"status\":\"degraded\"")
                .contains("\"reason\":\"DOWNSTREAM_ERROR\"");
    }

    @Test
    @DisplayName("per_leg_unexpected_401_NOT_cross_leg_collapse: scm returns 401 (misconfig) → degraded card, NOT 401 envelope (distinguishes from § 2.4.9.1)")
    void per_leg_401_does_not_cross_leg_collapse() {
        respond(GAP, 200, "{\"status\":\"UP\"}");
        respond(WMS, 200, "{\"status\":\"UP\"}");
        // SCM returns 401 — but actuator legs do not share an inbound credential,
        // so a 401 from one is NOT a 401 for all. Mapped to per-card degraded.
        respond(SCM, 401, "{}");
        respond(FINANCE, 200, "{\"status\":\"UP\"}");
        respond(ERP, 200, "{\"status\":\"UP\"}");
        respond(ECOMMERCE, 200, "{\"status\":\"UP\"}");

        ResponseEntity<String> response = callHealth(authHeaders());
        String body = response.getBody();

        // STILL 200 — no cross-leg 401 collapse. scm card degraded.
        assertThat(response.getStatusCode())
                .as("must NOT be 401 — actuator legs do not cross-leg-collapse. body:\n%s", body)
                .isEqualTo(HttpStatus.OK);
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"scm\"")
                .contains("\"status\":\"degraded\"");
    }

    // ------------------------------------------------------------------
    // All down
    // ------------------------------------------------------------------

    @Test
    @DisplayName("all_down_6x_503: 200 envelope with all 6 degraded cards; no forbidden anywhere")
    void all_down_6x_503() {
        respond(GAP, 503, "{}");
        respond(WMS, 503, "{}");
        respond(SCM, 503, "{}");
        respond(FINANCE, 503, "{}");
        respond(ERP, 503, "{}");
        respond(ECOMMERCE, 503, "{}");

        ResponseEntity<String> response = callHealth(authHeaders());
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("non-200 body:\n%s", body)
                .isEqualTo(HttpStatus.OK);
        // 6 cards all degraded; no forbidden anywhere.
        assertThat(body).as("body:\n%s", body).contains("\"status\":\"degraded\"");
        assertThat(body).as("body:\n%s", body).contains("\"domain\":\"ecommerce\"");
        assertThat(body).as("body:\n%s", body).doesNotContain("\"forbidden\"");
    }

    // ------------------------------------------------------------------
    // Inbound validation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("inbound_missing_tenant_400: absent X-Tenant-Id → 400 NO_ACTIVE_TENANT; no outbound fires")
    void inbound_missing_tenant_400() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + gapOidcJwt);
        // X-Tenant-Id intentionally absent.

        // MockWebServer.getRequestCount() is lifetime-accumulated across all
        // tests in this class. Snapshot before/after and assert no delta —
        // mirrors OperatorOverviewIntegrationTest.inbound_missing_tenant_400.
        Set<Integer> beforeCounts = snapshotRequestCounts();
        ResponseEntity<String> response = callHealth(h);
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("non-400 body:\n%s", body)
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body).as("body:\n%s", body).contains("\"code\":\"NO_ACTIVE_TENANT\"");

        // No outbound fired between before/after snapshot.
        Set<Integer> afterCounts = snapshotRequestCounts();
        assertThat(afterCounts).as("no outbound expected").isEqualTo(beforeCounts);
    }

    @Test
    @DisplayName("inbound_missing_auth_401: absent Authorization → 401 (Spring Security entry point); no outbound")
    void inbound_missing_auth_401() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Tenant-Id", "iam");
        // Authorization intentionally absent.

        Set<Integer> beforeCounts = snapshotRequestCounts();
        ResponseEntity<String> response = callHealth(h);

        assertThat(response.getStatusCode())
                .as("non-401 body:\n%s", response.getBody())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        Set<Integer> afterCounts = snapshotRequestCounts();
        assertThat(afterCounts).as("no outbound expected").isEqualTo(beforeCounts);
    }

    private static Set<Integer> snapshotRequestCounts() {
        Set<Integer> s = new HashSet<>();
        s.add(GAP.getRequestCount());
        s.add(WMS.getRequestCount());
        s.add(SCM.getRequestCount());
        s.add(FINANCE.getRequestCount());
        s.add(ERP.getRequestCount());
        s.add(ECOMMERCE.getRequestCount());
        return s;
    }
}
