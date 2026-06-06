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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-MONO-019 § 3.3 step 3 — console-bff entitlement-trust pass-through IT
 * (TASK-PC-BE-007).
 *
 * <p>Attests the ADR-MONO-017 D6.A HARD INVARIANT at the BFF for an
 * <b>entitlement-trust customer token</b>: an operator JWT carrying
 * {@code tenant_id=acme-corp} and {@code entitled_domains=["finance","wms"]}
 * must reach every downstream leg unchanged — the BFF performs no
 * strip, re-sign, or rewrite of any claim.
 *
 * <p>Three acceptance-criteria families:
 * <ol>
 *   <li><b>AC-A / AC-3 (token + tenant pass-through)</b> — for every non-GAP leg
 *       that fires, the {@code Authorization} header carries the exact same
 *       serialised JWT that was sent inbound (byte-identical), and the
 *       {@code X-Tenant-Id} header equals {@code "acme-corp"} verbatim.</li>
 *   <li><b>AC-B (per-card faithful surface)</b> — the 200 composition envelope
 *       surfaces finance + wms cards as {@code "status":"ok"} / data-present,
 *       and scm + erp cards as {@code "status":"forbidden"} /
 *       {@code "reason":"PERMISSION_DENIED"} — no central collapse.</li>
 *   <li><b>AC-C / AC-3 (no rewrite)</b> — the outbound JWT decoded via nimbus
 *       retains {@code entitled_domains=["finance","wms"]} and
 *       {@code tenant_id="acme-corp"} unchanged on every leg.</li>
 * </ol>
 *
 * <p>Finance is made to fire by supplying the required
 * {@code X-Finance-Default-Account-Id} header (option (a) activation path from
 * TASK-PC-FE-014); this lets both entitled domains (finance + wms) be
 * positively verified. The {@code entitled_domains} and {@code tenant_id}
 * claims are verified via nimbus {@link SignedJWT#parse(String)} on the
 * outbound bearer extracted from every {@link RecordedRequest}.
 *
 * <p>Stub + JWT harness mirrors {@link CrossTenantDenyIntegrationTest}:
 * self-contained 5 MockWebServer instances + RSA-2048 signing key +
 * JWKS publish + {@code @DynamicPropertySource} outbound base-url wiring.
 * This class boots an independent {@code @SpringBootTest} context.
 */
class EntitlementPassThroughIntegrationTest extends AbstractConsoleBffIntegrationTest {

    @SuppressWarnings("resource") static final MockWebServer GAP = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer WMS = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer SCM = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer FINANCE = new MockWebServer();
    @SuppressWarnings("resource") static final MockWebServer ERP = new MockWebServer();

    private static final String KID = "test-key-ep";
    /** Customer tenant for the entitlement-trust scenario. */
    private static final String CUSTOMER_TENANT = "acme-corp";
    /** Finance account id used to activate the finance leg (option (a)). */
    private static final String FINANCE_ACCOUNT_ID = "acme-acc-001";

    private static RSAKey rsaKey;
    /** Serialised operator JWT — the exact string asserted outbound (AC-A). */
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
                .subject("op-user-ep")
                .audience("console-bff")
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                .claim("tenant_id", CUSTOMER_TENANT)
                .claim("entitled_domains", List.of("finance", "wms"))
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

    private static void respond(MockWebServer server, int status, String json) {
        server.setDispatcher(new QueueDispatcher());
        server.enqueue(new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(json));
    }

    @Test
    @DisplayName("entitlement-trust acme-corp: tenant_id + entitled_domains forwarded verbatim to every leg; finance+wms ok, scm+erp forbidden (PERMISSION_DENIED) in 200 envelope; no BFF rewrite")
    void entitlementTrust_verbatimPassThrough_andPerCardFaithfulSurface() throws Exception {
        // finance + wms are the entitled domains → stub 200.
        // scm + erp are un-entitled → stub 403.
        // GAP always 200 (operator overview leg).
        // Finance is activated by supplying X-Finance-Default-Account-Id.
        respond(GAP, 200, "{\"page\":{\"totalElements\":5}}");
        respond(WMS, 200, "{\"snapshotTotal\":7}");
        respond(SCM, 403, "{}");
        respond(FINANCE, 200, "{\"totalAmount\":\"9999\"}");
        respond(ERP, 403, "{}");

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + operatorJwt);
        headers.set("X-Operator-Token", "op-tok-acme");
        headers.set("X-Tenant-Id", CUSTOMER_TENANT);
        // Activate finance leg (option (a) — without this header the finance
        // use case short-circuits as MISSING_PREREQUISITE before the outbound fires).
        headers.set("X-Finance-Default-Account-Id", FINANCE_ACCOUNT_ID);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/console/dashboards/operator-overview",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        String body = response.getBody();

        // ----------------------------------------------------------------
        // AC-B: 200 composition envelope; no central collapse on 403 legs.
        // ----------------------------------------------------------------
        assertThat(response.getStatusCode())
                .as("403 from scm/erp must NOT collapse the composition; body:\n%s", body)
                .isEqualTo(HttpStatus.OK);

        // Entitled domains (finance + wms) surface as ok / data-present.
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"wms\"")
                .contains("\"domain\":\"finance\"");
        // At minimum one of the entitled cards must be "ok" (wms data is always
        // present; finance card "ok" with the stubbed totalAmount payload).
        assertThat(body).as("body:\n%s", body)
                .contains("\"status\":\"ok\"");

        // Un-entitled domains (scm + erp) surface as forbidden/PERMISSION_DENIED —
        // mirrors CrossTenantDenyIntegrationTest + OperatorOverviewIntegrationTest
        // per_leg_forbidden_scm_403 assertion pattern.
        assertThat(body).as("body:\n%s", body)
                .contains("\"status\":\"forbidden\"")
                .contains("\"reason\":\"PERMISSION_DENIED\"");
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"scm\"")
                .contains("\"domain\":\"erp\"");

        // ----------------------------------------------------------------
        // AC-A + AC-C: token + tenant pass-through verification.
        //
        // Non-GAP legs (WMS, SCM, ERP + FINANCE when activated) receive the
        // inbound bearer verbatim (IamOidcAccessToken = Authorization bearer,
        // CredentialSelectionAdapter rows WMS/SCM/FINANCE/ERP).
        // ----------------------------------------------------------------

        // WMS leg — entitled, fires 200.
        RecordedRequest wmsReq = WMS.takeRequest(2, TimeUnit.SECONDS);
        assertThat(wmsReq).as("expected WMS outbound request").isNotNull();
        assertThat(wmsReq.getHeader("X-Tenant-Id"))
                .as("WMS: X-Tenant-Id must be forwarded verbatim (%s)", CUSTOMER_TENANT)
                .isEqualTo(CUSTOMER_TENANT);
        verifyEntitlementClaimsPreserved("WMS", wmsReq);

        // SCM leg — un-entitled, fires 403 (producer-side authority).
        RecordedRequest scmReq = SCM.takeRequest(2, TimeUnit.SECONDS);
        assertThat(scmReq).as("expected SCM outbound request (no central BFF gate)").isNotNull();
        assertThat(scmReq.getHeader("X-Tenant-Id"))
                .as("SCM: X-Tenant-Id must be forwarded verbatim (%s)", CUSTOMER_TENANT)
                .isEqualTo(CUSTOMER_TENANT);
        verifyEntitlementClaimsPreserved("SCM", scmReq);

        // ERP leg — un-entitled, fires 403 (producer-side authority).
        RecordedRequest erpReq = ERP.takeRequest(2, TimeUnit.SECONDS);
        assertThat(erpReq).as("expected ERP outbound request (no central BFF gate)").isNotNull();
        assertThat(erpReq.getHeader("X-Tenant-Id"))
                .as("ERP: X-Tenant-Id must be forwarded verbatim (%s)", CUSTOMER_TENANT)
                .isEqualTo(CUSTOMER_TENANT);
        verifyEntitlementClaimsPreserved("ERP", erpReq);

        // FINANCE leg — entitled, activated via X-Finance-Default-Account-Id.
        RecordedRequest finReq = FINANCE.takeRequest(2, TimeUnit.SECONDS);
        assertThat(finReq).as("expected FINANCE outbound request (X-Finance-Default-Account-Id set)").isNotNull();
        assertThat(finReq.getHeader("X-Tenant-Id"))
                .as("FINANCE: X-Tenant-Id must be forwarded verbatim (%s)", CUSTOMER_TENANT)
                .isEqualTo(CUSTOMER_TENANT);
        verifyEntitlementClaimsPreserved("FINANCE", finReq);
    }

    /**
     * Extracts the {@code Authorization} bearer from the recorded request,
     * parses the JWT via nimbus, and asserts that:
     * <ol>
     *   <li>The raw bearer string is byte-identical to the inbound
     *       {@code operatorJwt} (BFF does not re-mint the token).</li>
     *   <li>The {@code entitled_domains} claim equals {@code ["finance","wms"]}
     *       (no claim stripping).</li>
     *   <li>The {@code tenant_id} claim equals {@code "acme-corp"} (no
     *       tenant rewriting).</li>
     * </ol>
     *
     * @param legName descriptive label for AssertJ messages
     * @param req     the recorded outbound request
     */
    private static void verifyEntitlementClaimsPreserved(String legName, RecordedRequest req)
            throws Exception {
        String authHeader = req.getHeader(HttpHeaders.AUTHORIZATION);
        assertThat(authHeader)
                .as("%s: Authorization header must be present", legName)
                .isNotNull()
                .startsWith("Bearer ");

        String outboundBearer = authHeader.substring(7);

        // AC-A / AC-C (no rewrite): outbound bearer is byte-identical to inbound JWT.
        assertThat(outboundBearer)
                .as("%s: outbound bearer must be byte-identical to the inbound operatorJwt"
                        + " (BFF must not re-mint; ADR-017 D6.A)", legName)
                .isEqualTo(operatorJwt);

        // Parse and verify claims are intact (strip/re-sign would change the signature).
        JWTClaimsSet outboundClaims = SignedJWT.parse(outboundBearer).getJWTClaimsSet();

        assertThat(outboundClaims.getStringClaim("tenant_id"))
                .as("%s: tenant_id claim must be preserved verbatim", legName)
                .isEqualTo(CUSTOMER_TENANT);

        // entitled_domains is a JSON array; nimbus deserialises it as List.
        Object rawEntitled = outboundClaims.getClaim("entitled_domains");
        assertThat(rawEntitled)
                .as("%s: entitled_domains claim must be present (not stripped by BFF)", legName)
                .isNotNull();
        assertThat(rawEntitled)
                .as("%s: entitled_domains must contain [\"finance\",\"wms\"]", legName)
                .isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> entitledDomains = (List<String>) rawEntitled;
        assertThat(entitledDomains)
                .as("%s: entitled_domains claim must equal [finance,wms]", legName)
                .containsExactlyInAnyOrder("finance", "wms");
    }
}
