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
 * ADR-MONO-018 D5 — federation isolation regression, console-bff slice
 * (TASK-PC-BE-006).
 *
 * <p>Attests the ADR-MONO-017 D6 producer-side authority invariant at the BFF:
 * a <b>forged cross-tenant</b> request — an operator JWT whose {@code tenant_id}
 * ({@code gap}) differs from the requested {@code X-Tenant-Id} ({@code scm}),
 * with every downstream producer rejecting the foreign tenant (403) — must
 *
 * <ol>
 *   <li>be forwarded with {@code X-Tenant-Id} <b>verbatim</b> ({@code scm}, NOT
 *       rewritten to the token's {@code gap}) to every leg that fires — proving
 *       the BFF performs no central tenant re-scoping; and</li>
 *   <li>surface each producer's denial as a per-card {@code PERMISSION_DENIED}
 *       degrade inside a 200 composition envelope — the BFF never masks,
 *       re-scopes, or short-circuits the fan-out on a tenant basis.</li>
 * </ol>
 *
 * <p>Tenant authority is therefore producer-side (each producer's
 * {@code TenantClaimValidator} is the gate — see the sibling producer-side ITs
 * wms {@code OidcAuthIntegrationTest} / scm {@code MultiTenantIsolationIntegrationTest}
 * / finance {@code CrossTenantHttpIntegrationTest}). This IT guards against a
 * regression where the BFF starts rewriting the tenant or adds a central gate.
 *
 * <p>Stub + JWT harness mirrors {@code OperatorOverviewIntegrationTest}; this
 * class is self-contained (its own MockWebServer instances + signing key) so it
 * boots an independent {@code @SpringBootTest} context.
 */
class CrossTenantDenyIntegrationTest extends AbstractConsoleBffIntegrationTest {

    @SuppressWarnings("resource") static final MockWebServer GAP = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer WMS = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer SCM = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer FINANCE = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer ERP = new MockWebServer();

    private static final String KID = "test-key-d5";
    /** Operator's home tenant — the token claim. */
    private static final String TOKEN_TENANT = "gap";
    /** Forged/foreign tenant requested via X-Tenant-Id (cross-tenant). */
    private static final String FORGED_TENANT = "scm";

    private static RSAKey rsaKey;
    private static String operatorJwt;

    @LocalServerPort
    int port;

    @BeforeAll
    static void startStubsAndJwt() throws Exception {
        GAP.start();
        WMS.start();
        SCM.start();
        FINANCE.start();
        ERP.start();

        rsaKey = new RSAKeyGenerator(2048).keyID(KID).generate();
        publishJwks("{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}");

        JWSSigner signer = new RSASSASigner(rsaKey);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("http://test-issuer")
                .subject("op-user-d5")
                .audience("console-bff")
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                .claim("tenant_id", TOKEN_TENANT)
                .build();
        SignedJWT signed = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(), claims);
        signed.sign(signer);
        operatorJwt = signed.serialize();
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
        registry.add("consolebff.outbound.gap.base-url", () -> baseUrl(GAP));
        registry.add("consolebff.outbound.wms.base-url", () -> baseUrl(WMS));
        registry.add("consolebff.outbound.scm.base-url", () -> baseUrl(SCM));
        registry.add("consolebff.outbound.finance.base-url", () -> baseUrl(FINANCE));
        registry.add("consolebff.outbound.erp.base-url", () -> baseUrl(ERP));
    }

    private static String baseUrl(MockWebServer server) {
        try {
            if (server.getPort() <= 0) {
                server.start();
            }
        } catch (Exception ignored) { /* already started */ }
        return server.url("/").toString();
    }

    private static void respond(MockWebServer server, int status) {
        server.setDispatcher(new QueueDispatcher());
        server.enqueue(new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));
    }

    @Test
    @DisplayName("forged cross-tenant: X-Tenant-Id forwarded verbatim to every leg + all-403 surfaced as per-card PERMISSION_DENIED (200 envelope); producer-side authority, no central BFF gate")
    void forgedCrossTenant_verbatimPassThrough_andProducerSideDenial() throws Exception {
        // Every producer rejects the foreign tenant. FINANCE is not enqueued —
        // it short-circuits (MISSING_PREREQUISITE) without X-Finance-Default-Account-Id.
        respond(GAP, 403);
        respond(WMS, 403);
        respond(SCM, 403);
        respond(ERP, 403);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + operatorJwt);
        headers.set("X-Operator-Token", "op-tok-abc");
        // Forged: requested tenant differs from the token's tenant_id (gap).
        headers.set("X-Tenant-Id", FORGED_TENANT);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/console/dashboards/operator-overview",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        String body = response.getBody();

        // (1) No central collapse on 403 — per-card forbidden inside a 200 envelope
        //     (mirrors OperatorOverviewIntegrationTest per_leg_forbidden mapping).
        assertThat(response.getStatusCode())
                .as("403 from producers must NOT collapse the composition; body:\n%s", body)
                .isEqualTo(HttpStatus.OK);

        // (2) Producer-side denial surfaced, not masked.
        assertThat(body).as("body:\n%s", body)
                .contains("\"status\":\"forbidden\"")
                .contains("\"reason\":\"PERMISSION_DENIED\"");

        // (3) X-Tenant-Id forwarded VERBATIM (scm, not the token's gap) to every
        //     leg that fired — proves no central BFF tenant re-scoping. Taking a
        //     request from each stub also proves the outbound fired (no central
        //     gate short-circuit — producer-side authority).
        for (MockWebServer leg : new MockWebServer[]{GAP, WMS, SCM, ERP}) {
            RecordedRequest r = leg.takeRequest(2, TimeUnit.SECONDS);
            assertThat(r)
                    .as("expected outbound to fire on leg %s (no central tenant gate)", leg.getPort())
                    .isNotNull();
            assertThat(r.getHeader("X-Tenant-Id"))
                    .as("X-Tenant-Id must be forwarded verbatim (%s), not rewritten to the token tenant (%s)",
                            FORGED_TENANT, TOKEN_TENANT)
                    .isEqualTo(FORGED_TENANT);
        }
    }
}
