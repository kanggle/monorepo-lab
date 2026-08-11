package com.example.fanplatform.artist.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workload-identity enforcement on {@code /internal/artists/exists}: an IAM
 * {@code client_credentials} token carrying {@code artist.read} → 200; an
 * end-user token (including one carrying the END-USER resource scope
 * {@code fan-platform.artist.read}) → 403; no token → 401 (TASK-FAN-BE-045
 * AC-6, ADR-004 A, ADR-MONO-005). Mirrors membership-service's
 * {@code InternalAuthIntegrationTest} shape, run against the full Spring
 * context + real Testcontainers stack — the one layer the slice tests below
 * this class cannot reach (the actual network hop through the servlet
 * container, the real {@code fanplatform.internal.jwt.*} property wiring).
 */
class InternalArtistAuthIntegrationTest extends ArtistServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String url() {
        return "http://localhost:" + port
                + "/internal/artists/exists?accountId=acc-1&tenantId=fan-platform";
    }

    private ResponseEntity<String> call(HttpHeaders headers) {
        return rest.exchange(url(), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("workload-identity client_credentials token (artist.read scope) → 200 { exists: ... }")
    void workloadTokenAllowed() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.signWorkloadToken("community-service-client"));
        ResponseEntity<String> res = call(h);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("exists");
    }

    @Test
    @DisplayName("end-user token → 403 FORBIDDEN, NOT 200")
    void endUserToken403() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.signFanToken("fan-1"));
        ResponseEntity<String> res = call(h);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("🔴 a token carrying fan-platform.artist.read (END-USER resource scope) → 403, NOT 200")
    void fanResourceScopeToken403() {
        // The wrong-family scope pin, run through the real network hop + the real
        // fanplatform.internal.jwt.* property wiring this base sets up — not just the
        // in-process slice tests. If REQUIRED_WORKLOAD_SCOPE ever keyed on
        // fan-platform.artist.read instead of artist.read, this is the test that would
        // catch every logged-in fan gaining access end-to-end.
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.signFanResourceScopedToken("fan-1"));
        ResponseEntity<String> res = call(h);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("no token → 401")
    void noToken401() {
        ResponseEntity<String> res = call(new HttpHeaders());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
