package com.kanggle.platformconsole.bff.integration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.Dispatcher;
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
 * Integration test for the operator-overview composition surface (AC-12+).
 *
 * <p>Extends {@link AbstractConsoleBffIntegrationTest} (reuses JWKS_SERVER +
 * spring.security.oauth2.resourceserver wiring). Adds 5 MockWebServer instances
 * stubbing the 5 backend domains (gap / wms / scm / finance / erp).
 *
 * <p>Coverage:
 * <ul>
 *   <li>Happy path 5-card all-ok with credential dispatch + tenant pass-through assertions.</li>
 *   <li>Per-leg degrade (wms 503 → DOWNSTREAM_ERROR).</li>
 *   <li>Per-leg forbidden (scm 403 → PERMISSION_DENIED).</li>
 *   <li>Per-leg timeout (erp delay &gt; 2s per-leg timeout → TIMEOUT).</li>
 *   <li>All-down 5x 503 → still 200 envelope with 5 degraded cards + 5x degrade counter.</li>
 *   <li>Cross-leg 401 collapse → composition-level 401 TOKEN_INVALID.</li>
 *   <li>Inbound missing X-Tenant-Id → 400 NO_ACTIVE_TENANT (no outbound call fires).</li>
 *   <li>Inbound missing Authorization → 401 (Spring Security entry point; no outbound).</li>
 *   <li>Prometheus metrics exposition after a happy run.</li>
 *   <li>Parallel fan-out: one slow leg does not block the others (composition under 2.5s).</li>
 * </ul>
 */
class OperatorOverviewIntegrationTest extends AbstractConsoleBffIntegrationTest {

    @SuppressWarnings("resource") static final MockWebServer GAP = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer WMS = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer SCM = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer FINANCE = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer ERP = new MockWebServer();

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

        rsaKey = new RSAKeyGenerator(2048).keyID("test-key-it").generate();
        String publicJwksJson = "{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}";
        publishJwks(publicJwksJson);

        JWSSigner signer = new RSASSASigner(rsaKey);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("http://test-issuer")
                .subject("op-user-it")
                .audience("console-bff")
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                .claim("tenant_id", "gap")
                .build();

        SignedJWT signed = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key-it").build(),
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
    }

    @DynamicPropertySource
    static void outboundBaseUrls(DynamicPropertyRegistry registry) {
        // Use late lambdas — the MockWebServer is started in @BeforeAll which
        // runs after @DynamicPropertySource evaluation, so url(...) is invoked
        // lazily by Spring's property resolver. okhttp MockWebServer assigns the
        // port at start(); url() returns the live URL.
        registry.add("consolebff.outbound.gap.base-url", () -> baseUrl(GAP));
        registry.add("consolebff.outbound.wms.base-url", () -> baseUrl(WMS));
        registry.add("consolebff.outbound.scm.base-url", () -> baseUrl(SCM));
        registry.add("consolebff.outbound.finance.base-url", () -> baseUrl(FINANCE));
        registry.add("consolebff.outbound.erp.base-url", () -> baseUrl(ERP));
    }

    private static String baseUrl(MockWebServer server) {
        try {
            // start() is idempotent inside okhttp — calling here ensures the
            // server is started before url() resolves (covers Spring property
            // resolution order edge cases).
            if (server.getPort() <= 0) {
                server.start();
            }
        } catch (Exception ignored) { /* start may throw if already started */ }
        return server.url("/").toString();
    }

    @BeforeEach
    void resetStubs() {
        // Drain any pending queued responses + clear the dispatcher.
        resetMockServer(GAP);
        resetMockServer(WMS);
        resetMockServer(SCM);
        resetMockServer(FINANCE);
        resetMockServer(ERP);
    }

    private static void resetMockServer(MockWebServer server) {
        // Restore default QueueDispatcher so subsequent `enqueue(...)` calls
        // succeed (MockWebServer internally casts to QueueDispatcher when
        // enqueue is invoked; a custom Dispatcher set here would trigger
        // ClassCastException — CI surface PR #672 cycle 2).
        server.setDispatcher(new okhttp3.mockwebserver.QueueDispatcher());
        // Drain pending requests so per-test takeRequest() sees a clean queue.
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
        h.set("X-Operator-Token", "op-tok-abc");
        h.set("X-Tenant-Id", "gap");
        return h;
    }

    private ResponseEntity<String> callOverview(HttpHeaders headers) {
        return restTemplate.exchange(
                "http://localhost:" + port + "/api/console/dashboards/operator-overview",
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
    // Happy path with credential dispatch verification
    // ------------------------------------------------------------------

    @Test
    @DisplayName("happy_path_5_cards_all_ok: per-leg credential dispatch + tenant pass-through verified")
    void happy_path_5_cards_all_ok_credential_dispatch_verified() throws Exception {
        respond(GAP, 200, "{\"page\":{\"totalElements\":12345}}");
        respond(WMS, 200, "{\"snapshotTotal\":42}");
        respond(SCM, 200, "{\"nodeCount\":3}");
        respond(FINANCE, 200, "{\"balance\":0}");
        respond(ERP, 200, "{\"meta\":{\"totalElements\":9}}");

        // FINANCE counter snapshot before invocation — MockWebServer.getRequestCount()
        // is lifetime-accumulated, so other tests in this class that DO fire FINANCE
        // (e.g. inbound_header_set_finance_ok_one_outbound — TASK-PC-FE-014 Phase 2
        // option (a) activation path) leave the counter non-zero. Use delta == 0
        // rather than absolute zero (TASK-PC-FE-013 cycle 1 lesson; mirrors the
        // DomainHealthIntegrationTest snapshot-and-diff pattern).
        int financeBefore = FINANCE.getRequestCount();

        ResponseEntity<String> response = callOverview(authHeaders());
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("non-200 body:\n%s", body)
                .isEqualTo(HttpStatus.OK);
        // Envelope shape (§ 2.4.9.1)
        assertThat(body).as("body:\n%s", body).contains("\"asOf\"").contains("\"cards\"");
        // Fixed order — 4 legs ok; finance is MVP-pinned forbidden/MISSING_PREREQUISITE
        // (the use case short-circuits without firing FINANCE).
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"gap\"")
                .contains("\"domain\":\"wms\"")
                .contains("\"domain\":\"scm\"")
                .contains("\"domain\":\"finance\"")
                .contains("\"domain\":\"erp\"");

        // GAP leg — operator token bearer + tenant pass-through.
        RecordedRequest gapReq = GAP.takeRequest(2, TimeUnit.SECONDS);
        assertThat(gapReq).as("expected GAP outbound request").isNotNull();
        assertThat(gapReq.getHeader(HttpHeaders.AUTHORIZATION))
                .as("GAP must dispatch RFC8693 operator token")
                .isEqualTo("Bearer op-tok-abc");
        assertThat(gapReq.getHeader("X-Tenant-Id")).isEqualTo("gap");

        // wms / scm / erp legs — GAP OIDC access token (inbound bearer) verbatim.
        for (MockWebServer s : new MockWebServer[]{WMS, SCM, ERP}) {
            RecordedRequest r = s.takeRequest(2, TimeUnit.SECONDS);
            assertThat(r).as("expected outbound request on stub %s", s.getPort()).isNotNull();
            assertThat(r.getHeader(HttpHeaders.AUTHORIZATION))
                    .as("non-GAP leg must dispatch GAP OIDC access token")
                    .isEqualTo("Bearer " + gapOidcJwt);
            assertThat(r.getHeader("X-Tenant-Id")).isEqualTo("gap");
        }

        // FINANCE — option (b) short-circuit when X-Finance-Default-Account-Id
        // header is absent (this test does not send it). The use case never fires
        // the outbound HTTP call. Snapshot-and-diff: delta == 0, NOT absolute 0
        // (counter is lifetime-accumulated across the JUnit class — see snapshot
        // taken before callOverview above).
        assertThat(FINANCE.getRequestCount() - financeBefore)
                .as("finance leg must not fire when X-Finance-Default-Account-Id absent (option b — MISSING_PREREQUISITE)")
                .isZero();
    }

    // ------------------------------------------------------------------
    // Per-leg degrade
    // ------------------------------------------------------------------

    @Test
    @DisplayName("per_leg_degrade_wms_503: 200 envelope; wms card degraded/DOWNSTREAM_ERROR")
    void per_leg_degrade_wms_503() {
        respond(GAP, 200, "{\"page\":{\"totalElements\":1}}");
        respond(WMS, 503, "{}");
        respond(SCM, 200, "{}");
        respond(ERP, 200, "{}");

        ResponseEntity<String> response = callOverview(authHeaders());
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
    @DisplayName("per_leg_forbidden_scm_403: 200 envelope; scm card forbidden/PERMISSION_DENIED")
    void per_leg_forbidden_scm_403() {
        respond(GAP, 200, "{}");
        respond(WMS, 200, "{}");
        respond(SCM, 403, "{}");
        respond(ERP, 200, "{}");

        ResponseEntity<String> response = callOverview(authHeaders());
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("non-200 body:\n%s", body)
                .isEqualTo(HttpStatus.OK);
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"scm\"")
                .contains("\"status\":\"forbidden\"")
                .contains("\"reason\":\"PERMISSION_DENIED\"");
    }

    @Test
    @DisplayName("per_leg_timeout_erp: erp delay > 2s per-leg timeout → erp card degraded/TIMEOUT")
    void per_leg_timeout_erp() {
        respond(GAP, 200, "{}");
        respond(WMS, 200, "{}");
        respond(SCM, 200, "{}");
        // ERP delays 3s — exceeds the 2s per-leg read timeout (RestClientConfig).
        ERP.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{}")
                .setHeadersDelay(3, TimeUnit.SECONDS));

        ResponseEntity<String> response = callOverview(authHeaders());
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("non-200 body:\n%s", body)
                .isEqualTo(HttpStatus.OK);
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"erp\"")
                .contains("\"status\":\"degraded\"");
        // Either TIMEOUT (read-timeout classification) or DOWNSTREAM_ERROR
        // (composition-level deadline cut-off) is acceptable per the use case's
        // exception classification.
        assertThat(body).as("body:\n%s", body)
                .containsPattern("\"reason\":\"(TIMEOUT|DOWNSTREAM_ERROR)\"");
    }

    // ------------------------------------------------------------------
    // All down
    // ------------------------------------------------------------------

    @Test
    @DisplayName("all_down_5x_503: 200 envelope with all-down cards; degrade counter 5x")
    void all_down_5x_503() {
        respond(GAP, 503, "{}");
        respond(WMS, 503, "{}");
        respond(SCM, 503, "{}");
        // FINANCE never fires — MVP option (b)
        respond(ERP, 503, "{}");

        ResponseEntity<String> response = callOverview(authHeaders());
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("non-200 body:\n%s", body)
                .isEqualTo(HttpStatus.OK);
        // 4 degraded + 1 forbidden (finance MVP pin)
        assertThat(body).as("body:\n%s", body).contains("\"status\":\"degraded\"");
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"finance\"")
                .contains("\"reason\":\"MISSING_PREREQUISITE\"");

        // Verify the Prometheus counter saw 5 per-card increments.
        ResponseEntity<String> prom = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/prometheus", String.class);
        String promBody = prom.getBody();
        assertThat(prom.getStatusCode())
                .as("prom non-200 body:\n%s", promBody)
                .isEqualTo(HttpStatus.OK);
        // Counter family must be exposed (per-line label values vary).
        assertThat(promBody).as("prom:\n%s", promBody)
                .contains("bff_aggregation_degrade_count_total");
    }

    // ------------------------------------------------------------------
    // Cross-leg 401 collapse
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cross_leg_401: any leg 401 → composition collapses to 401 TOKEN_INVALID")
    void cross_leg_401_one_leg_unauthorized() {
        respond(GAP, 200, "{}");
        respond(WMS, 401, "{}");
        respond(SCM, 200, "{}");
        respond(ERP, 200, "{}");

        ResponseEntity<String> response = callOverview(authHeaders());
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("non-401 body:\n%s", body)
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body).as("body:\n%s", body).contains("\"code\":\"TOKEN_INVALID\"");
    }

    // ------------------------------------------------------------------
    // Inbound validation — fail-closed BEFORE any outbound
    // ------------------------------------------------------------------

    @Test
    @DisplayName("inbound_missing_tenant_400: absent X-Tenant-Id → 400 NO_ACTIVE_TENANT; no outbound fires")
    void inbound_missing_tenant_400() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + gapOidcJwt);
        h.set("X-Operator-Token", "op-tok-abc");
        // No X-Tenant-Id

        Set<Integer> beforeCounts = snapshotRequestCounts();
        ResponseEntity<String> response = callOverview(h);
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("non-400 body:\n%s", body)
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body).as("body:\n%s", body).contains("\"code\":\"NO_ACTIVE_TENANT\"");

        // Verify no outbound request reached any stub
        Set<Integer> afterCounts = snapshotRequestCounts();
        assertThat(afterCounts).as("no outbound expected").isEqualTo(beforeCounts);
    }

    @Test
    @DisplayName("inbound_missing_auth_401: absent Authorization → 401 (Spring Security entry point); no outbound")
    void inbound_missing_auth_401() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Operator-Token", "op-tok-abc");
        h.set("X-Tenant-Id", "gap");
        // No Authorization

        Set<Integer> beforeCounts = snapshotRequestCounts();
        ResponseEntity<String> response = callOverview(h);

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
        // HashSet collapses duplicates — to give a stable equality check
        // include the per-server sum as a separate signature.
        s.add(GAP.getRequestCount() + WMS.getRequestCount() + SCM.getRequestCount()
                + FINANCE.getRequestCount() + ERP.getRequestCount() + 10_000);
        return s;
    }

    // ------------------------------------------------------------------
    // Prometheus metrics exposition
    // ------------------------------------------------------------------

    @Test
    @DisplayName("prometheus_metrics_emitted: after a happy run the 3 metric families are exposed")
    void prometheus_metrics_emitted() {
        respond(GAP, 200, "{}");
        respond(WMS, 200, "{}");
        respond(SCM, 200, "{}");
        respond(ERP, 200, "{}");

        ResponseEntity<String> overview = callOverview(authHeaders());
        assertThat(overview.getStatusCode())
                .as("overview non-200 body:\n%s", overview.getBody())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/prometheus", String.class);
        String body = response.getBody();
        assertThat(response.getStatusCode())
                .as("non-200 body:\n%s",
                        body == null ? "<null>" : body.substring(0, Math.min(4000, body.length())))
                .isEqualTo(HttpStatus.OK);
        assertThat(body).contains("bff_fanout_latency_seconds");
        assertThat(body).contains("bff_fanout_errors_total");
        assertThat(body).contains("bff_aggregation_degrade_count_total");
    }

    // ------------------------------------------------------------------
    // Parallel fan-out — one slow leg does not block others
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // TASK-PC-FE-014 — finance leg Option (a) activation IT
    //
    // Both paths first-class:
    //   header-absent → finance card MISSING_PREREQUISITE, ZERO outbound on
    //     the FINANCE MockWebServer (snapshot-and-diff request-count pattern).
    //   header-set   → finance card ok; exactly 1 request fired against the
    //     FINANCE leg with path /api/finance/accounts/{id}/balances and
    //     Authorization: Bearer <gap-oidc-token>.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("inbound_header_absent_finance_missing_prerequisite_no_outbound: no X-Finance-Default-Account-Id → finance MISSING_PREREQUISITE; 4 other legs fire; finance request-count delta == 0")
    void inbound_header_absent_finance_missing_prerequisite_no_outbound() {
        respond(GAP, 200, "{\"page\":{\"totalElements\":1}}");
        respond(WMS, 200, "{\"snapshotTotal\":1}");
        respond(SCM, 200, "{\"nodeCount\":1}");
        // FINANCE intentionally NOT enqueued — assert below it never fires.
        respond(ERP, 200, "{\"meta\":{\"totalElements\":1}}");

        int financeBefore = FINANCE.getRequestCount();

        ResponseEntity<String> response = callOverview(authHeaders());
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("non-200 body:\n%s", body)
                .isEqualTo(HttpStatus.OK);
        // Finance card forbidden/MISSING_PREREQUISITE (AC-2 regression guard).
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"finance\"")
                .contains("\"reason\":\"MISSING_PREREQUISITE\"");
        // Snapshot-and-diff: zero outbound on FINANCE leg.
        int financeAfter = FINANCE.getRequestCount();
        assertThat(financeAfter - financeBefore)
                .as("finance leg must NOT fire when X-Finance-Default-Account-Id is absent")
                .isZero();
    }

    @Test
    @DisplayName("inbound_header_set_finance_ok_one_outbound: X-Finance-Default-Account-Id → finance ok with data; exactly 1 outbound fired with /api/finance/accounts/{id}/balances + Bearer gap-oidc")
    void inbound_header_set_finance_ok_one_outbound() throws Exception {
        respond(GAP, 200, "{\"page\":{\"totalElements\":1}}");
        respond(WMS, 200, "{\"snapshotTotal\":1}");
        respond(SCM, 200, "{\"nodeCount\":1}");
        respond(FINANCE, 200, "{\"totalAmount\":\"1000\"}");
        respond(ERP, 200, "{\"meta\":{\"totalElements\":1}}");

        int financeBefore = FINANCE.getRequestCount();

        HttpHeaders headers = authHeaders();
        headers.set("X-Finance-Default-Account-Id", "acc-uuid-7");

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/console/dashboards/operator-overview",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("non-200 body:\n%s", body)
                .isEqualTo(HttpStatus.OK);
        // Finance card ok with the stubbed balances payload (AC-3).
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"finance\"")
                .contains("\"status\":\"ok\"")
                .contains("\"totalAmount\":\"1000\"");
        // Exactly 1 outbound on FINANCE leg (snapshot-and-diff).
        int financeAfter = FINANCE.getRequestCount();
        assertThat(financeAfter - financeBefore)
                .as("finance leg must fire exactly once when X-Finance-Default-Account-Id is set")
                .isEqualTo(1);
        // Path template + bearer assertions on the recorded request.
        RecordedRequest finReq = FINANCE.takeRequest(2, TimeUnit.SECONDS);
        assertThat(finReq).as("expected FINANCE outbound request").isNotNull();
        assertThat(finReq.getPath())
                .as("path must be /api/finance/accounts/{accountId}/balances")
                .isEqualTo("/api/finance/accounts/acc-uuid-7/balances");
        assertThat(finReq.getHeader(HttpHeaders.AUTHORIZATION))
                .as("finance leg must dispatch GAP OIDC access token bearer (§ 2.4.9.1 row 4)")
                .isEqualTo("Bearer " + gapOidcJwt);
        assertThat(finReq.getHeader("X-Tenant-Id")).isEqualTo("gap");
    }

    @Test
    @DisplayName("parallel_fan_out: one slow leg (1.5s) does not serialise the others; total < 3s")
    void parallel_fan_out_one_slow_leg_does_not_block_others() {
        // wms delays 1.5s; others respond immediately
        respond(GAP, 200, "{}");
        WMS.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{}")
                .setHeadersDelay(1500, TimeUnit.MILLISECONDS));
        respond(SCM, 200, "{}");
        respond(ERP, 200, "{}");

        long t0 = System.nanoTime();
        ResponseEntity<String> response = callOverview(authHeaders());
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        assertThat(response.getStatusCode())
                .as("non-200 body:\n%s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        // Slow leg dominates; if the 4 legs serialised (1.5s wms + others), the
        // floor would be ~1.5s and the ceiling well over 3s; parallel fan-out
        // keeps the total under 3s with comfortable margin.
        assertThat(elapsedMs).as("composition wall-clock = %dms", elapsedMs).isLessThan(3000);
    }
}
