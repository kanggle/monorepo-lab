package com.wms.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The identity-header security boundary end-to-end through the real filter chain: wms's
 * {@code IdentityHeaderStripFilter} (removes whatever the client claimed) immediately
 * followed by its {@code JwtHeaderEnrichmentFilter} (re-asserts only what the verified JWT
 * vouches for). Both filters are wired by {@code GatewayIdentityConfig}; the downstream
 * MockWebServer captures exactly what crossed the edge.
 *
 * <p>wms's injected-header policy (ADR-MONO-035 4b-2a) is the smallest of the three gateways
 * and is asserted precisely here — including two <em>deliberate absences</em> that iam/fan do
 * not test: {@code X-Tenant-Id} and {@code X-Account-Type} are stripped but NOT re-injected.
 */
@Tag("integration")
class GatewayIdentityHeaderIntegrationTest extends GatewayIntegrationBase {

    @Test
    void spoofedIdentityHeadersAreStrippedAndOverwrittenFromTheVerifiedJwt() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true}"));

        String token = jwt.signToken("operator-42", "MASTER_WRITE", 300,
                Map.of("email", "operator-42@test.local"));

        webTestClient.get().uri("/api/v1/master/warehouses")
                // Client attempts to spoof identity + tenant + account-type headers. The strip
                // filter must remove all of them before enrichment re-asserts the JWT-derived set.
                .header("Authorization", "Bearer " + token)
                .header("X-User-Id", "attacker")
                .header("X-Actor-Id", "attacker")
                .header("X-User-Email", "attacker@evil.test")
                .header("X-User-Role", "SUPER_ADMIN")
                .header("X-Tenant-Id", "evil-tenant")
                .header("X-Account-Type", "ADMIN")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).as("downstream stub did not receive the request").isNotNull();

        // Enriched from the verified JWT — the spoofed values are gone.
        assertThat(received.getHeader("X-User-Id"))
                .as("X-User-Id must be the JWT subject, not the client-supplied value")
                .isEqualTo("operator-42");
        assertThat(received.getHeader("X-Actor-Id"))
                .as("X-Actor-Id must also be re-asserted from the JWT subject")
                .isEqualTo("operator-42");
        assertThat(received.getHeader("X-User-Email"))
                .as("X-User-Email must be the JWT email claim")
                .isEqualTo("operator-42@test.local");
        assertThat(received.getHeader("X-User-Role"))
                .as("X-User-Role must be the JWT role, not the spoofed SUPER_ADMIN")
                .isEqualTo("MASTER_WRITE");

        // wms strips these but — unlike X-User-* — deliberately does NOT re-inject them
        // (ADR-MONO-035 4b-2a). The spoofed values must be simply gone, not overwritten.
        assertThat(received.getHeader("X-Tenant-Id"))
                .as("wms strips X-Tenant-Id and does not re-inject it (strict tenant gate)")
                .isNull();
        assertThat(received.getHeader("X-Account-Type"))
                .as("wms strips X-Account-Type and does not re-inject it (no downstream reads it)")
                .isNull();
    }

    @Test
    void rolesArrayIsJoinedIntoXUserRole() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true}"));

        // A token carrying a `roles` array (the canonical operator shape) — JwtClaims#role
        // joins it on "," with defined precedence over the scalar `role`.
        String token = jwt.signToken("operator-77", "MASTER_READ", 300,
                Map.of("roles", java.util.List.of("MASTER_WRITE", "MASTER_READ"),
                        "email", "operator-77@test.local"));

        webTestClient.get().uri("/api/v1/master/warehouses")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getHeader("X-User-Role"))
                .as("roles[] takes precedence over the scalar role claim and is comma-joined")
                .isEqualTo("MASTER_WRITE,MASTER_READ");
    }
}
