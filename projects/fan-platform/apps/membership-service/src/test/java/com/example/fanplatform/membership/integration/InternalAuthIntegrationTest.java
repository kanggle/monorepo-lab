package com.example.fanplatform.membership.integration;

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
 * Workload-identity enforcement on {@code /internal/membership/access}:
 * client_credentials token → 200; end-user token → 403; no token → 401
 * (ADR-MONO-005, AC-5).
 */
class InternalAuthIntegrationTest extends MembershipServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String url() {
        return "http://localhost:" + port
                + "/internal/membership/access?accountId=acc1&tier=MEMBERS_ONLY&tenantId=fan-platform";
    }

    private ResponseEntity<String> call(HttpHeaders headers) {
        return rest.exchange(url(), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("workload-identity client_credentials token → 200")
    void workloadTokenAllowed() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.signWorkloadToken("svc-community"));
        ResponseEntity<String> res = call(h);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("allowed");
    }

    @Test
    @DisplayName("end-user token → 403 FORBIDDEN")
    void endUserToken403() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.signFanToken("acc1"));
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
