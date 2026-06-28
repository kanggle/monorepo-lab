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
import okhttp3.mockwebserver.QueueDispatcher;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the console notification aggregator surface
 * (ADR-MONO-043 P3a / D2). Mirrors {@link OperatorOverviewIntegrationTest}'s
 * MockWebServer JWKS + bearer setup; stubs only the erp inbox (the Phase-1
 * domain set) since the aggregator fans into the configured domains.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Happy path — 200 + merged items + injected/preserved {@code sourceDomain}
 *       + {@code degradedDomains} empty; erp leg dispatches the IAM OIDC token
 *       bearer and sends <b>NO {@code X-Tenant-Id}</b> (D6 / contract § 3).</li>
 *   <li>Failure isolation (D5) — erp 503 → still <b>200</b> +
 *       {@code degradedDomains=["erp"]} + empty items (never a 5xx).</li>
 *   <li>Inbound missing Authorization → 401 (Spring Security; no outbound).</li>
 *   <li>Mark-read proxied to erp; unknown sourceDomain → 404.</li>
 * </ul>
 *
 * <p>Note: console-bff's IT harness uses okhttp {@code MockWebServer} (not the
 * WireMock literal) — the established pattern shared by every console-bff IT in
 * the {@code Integration (platform-console console-bff, Testcontainers + WireMock
 * JWKS)} CI lane (see {@link AbstractConsoleBffIntegrationTest}).
 */
class NotificationAggregatorIntegrationTest extends AbstractConsoleBffIntegrationTest {

    @SuppressWarnings("resource")
    static final MockWebServer ERP = new MockWebServer();

    private static RSAKey rsaKey;
    private static String gapOidcJwt;

    @LocalServerPort
    int port;

    @BeforeAll
    static void startStubsAndJwt() throws Exception {
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
                .claim("tenant_id", "erp")
                .build();

        SignedJWT signed = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key-it").build(),
                claims);
        signed.sign(signer);
        gapOidcJwt = signed.serialize();
    }

    @AfterAll
    static void stopStubs() throws Exception {
        ERP.shutdown();
    }

    @DynamicPropertySource
    static void outboundBaseUrls(DynamicPropertyRegistry registry) {
        registry.add("consolebff.outbound.erp.base-url", () -> baseUrl(ERP));
        // Phase-1 domain set — erp only.
        registry.add("consolebff.notifications.domains", () -> "erp");
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
        ERP.setDispatcher(new QueueDispatcher());
        try {
            while (ERP.takeRequest(0, TimeUnit.MILLISECONDS) != null) {
                // drain
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
        // Deliberately NO X-Tenant-Id — the aggregator does not require it
        // (erp resolves tenant from the JWT tenant_id claim, D6 / contract § 3).
        return h;
    }

    private ResponseEntity<String> callInbox(HttpHeaders headers) {
        return restTemplate.exchange(
                "http://localhost:" + port + "/api/console/notifications/inbox?page=0&size=20",
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
    // Happy path — merged items + sourceDomain + credential dispatch + NO X-Tenant-Id
    // ------------------------------------------------------------------

    @Test
    @DisplayName("happy_path_merged_items: 200 + items + sourceDomain=erp; IAM OIDC bearer + NO X-Tenant-Id")
    void happy_path_merged_items() throws Exception {
        respond(ERP, 200, "{"
                + "\"data\":["
                + "  {\"id\":\"n1\",\"sourceDomain\":\"erp\",\"type\":\"APPROVAL_SUBMITTED\","
                + "   \"title\":\"t1\",\"body\":\"b1\",\"read\":false,\"createdAt\":\"2026-06-28T01:00:00Z\"},"
                + "  {\"id\":\"n2\",\"sourceDomain\":\"erp\",\"type\":\"APPROVAL_APPROVED\","
                + "   \"title\":\"t2\",\"body\":\"b2\",\"read\":false,\"createdAt\":\"2026-06-28T03:00:00Z\"}"
                + "],"
                + "\"meta\":{\"page\":0,\"size\":20,\"totalElements\":2}}");

        ResponseEntity<String> response = callInbox(authHeaders());
        String body = response.getBody();

        assertThat(response.getStatusCode()).as("non-200 body:\n%s", body).isEqualTo(HttpStatus.OK);
        assertThat(body).as("body:\n%s", body)
                .contains("\"asOf\"")
                .contains("\"items\"")
                .contains("\"degradedDomains\":[]")
                .contains("\"sourceDomain\":\"erp\"")
                .contains("\"id\":\"n1\"")
                .contains("\"id\":\"n2\"")
                .contains("\"totalElements\":2");
        // Newest-first sort — n2 (03:00) precedes n1 (01:00).
        assertThat(body.indexOf("\"id\":\"n2\"")).isLessThan(body.indexOf("\"id\":\"n1\""));

        // erp leg — IAM OIDC access token bearer + NO X-Tenant-Id (D6 / contract § 3).
        RecordedRequest erpReq = ERP.takeRequest(2, TimeUnit.SECONDS);
        assertThat(erpReq).as("expected erp outbound request").isNotNull();
        assertThat(erpReq.getPath()).startsWith("/api/erp/notifications");
        assertThat(erpReq.getHeader(HttpHeaders.AUTHORIZATION))
                .as("erp leg must dispatch the IAM OIDC access token")
                .isEqualTo("Bearer " + gapOidcJwt);
        assertThat(erpReq.getHeader("X-Tenant-Id"))
                .as("erp leg must NOT send X-Tenant-Id (D6 / contract § 3)")
                .isNull();
    }

    // ------------------------------------------------------------------
    // Failure isolation (D5) — erp down → still 200 + degradedDomains
    // ------------------------------------------------------------------

    @Test
    @DisplayName("failure_isolation_erp_down_503: still 200 + degradedDomains=[erp] + empty items (never 5xx)")
    void failure_isolation_erp_down() {
        respond(ERP, 503, "{}");

        ResponseEntity<String> response = callInbox(authHeaders());
        String body = response.getBody();

        assertThat(response.getStatusCode())
                .as("aggregator MUST be 200 when a domain is down (D5); body:\n%s", body)
                .isEqualTo(HttpStatus.OK);
        assertThat(body).as("body:\n%s", body)
                .contains("\"degradedDomains\":[\"erp\"]")
                .contains("\"items\":[]");
    }

    // ------------------------------------------------------------------
    // Inbound auth — fail-closed before any outbound
    // ------------------------------------------------------------------

    @Test
    @DisplayName("inbound_missing_auth_401: absent Authorization → 401 (Spring Security); no outbound")
    void inbound_missing_auth_401() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Operator-Token", "op-tok-abc");
        // No Authorization

        int before = ERP.getRequestCount();
        ResponseEntity<String> response = callInbox(h);

        assertThat(response.getStatusCode())
                .as("non-401 body:\n%s", response.getBody())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ERP.getRequestCount()).as("no outbound expected").isEqualTo(before);
    }

    // ------------------------------------------------------------------
    // Mark-read proxy (contract § 4.5)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("mark_read_proxied_to_erp: POST proxied to erp with IAM OIDC bearer + NO X-Tenant-Id")
    void mark_read_proxied_to_erp() throws Exception {
        respond(ERP, 200, "{\"data\":{\"id\":\"n1\",\"sourceDomain\":\"erp\",\"read\":true}}");

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/console/notifications/erp/n1/read",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                String.class);
        String body = response.getBody();

        assertThat(response.getStatusCode()).as("non-200 body:\n%s", body).isEqualTo(HttpStatus.OK);
        assertThat(body).as("body:\n%s", body).contains("\"read\":true");

        RecordedRequest erpReq = ERP.takeRequest(2, TimeUnit.SECONDS);
        assertThat(erpReq).as("expected erp mark-read request").isNotNull();
        assertThat(erpReq.getMethod()).isEqualTo("POST");
        assertThat(erpReq.getPath()).isEqualTo("/api/erp/notifications/n1/read");
        assertThat(erpReq.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + gapOidcJwt);
        assertThat(erpReq.getHeader("X-Tenant-Id"))
                .as("erp mark-read must NOT send X-Tenant-Id (D6 / contract § 3)")
                .isNull();
    }

    @Test
    @DisplayName("mark_read_unknown_domain_404: unknown sourceDomain → 404 NOTIFICATION_NOT_FOUND; no outbound")
    void mark_read_unknown_domain_404() {
        int before = ERP.getRequestCount();

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/console/notifications/wms/n1/read",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                String.class);
        String body = response.getBody();

        assertThat(response.getStatusCode()).as("non-404 body:\n%s", body).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body).as("body:\n%s", body).contains("\"code\":\"NOTIFICATION_NOT_FOUND\"");
        assertThat(ERP.getRequestCount()).as("no outbound expected for unknown domain").isEqualTo(before);
    }
}
