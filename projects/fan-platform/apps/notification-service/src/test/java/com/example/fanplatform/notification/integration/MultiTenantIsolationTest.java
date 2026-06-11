package com.example.fanplatform.notification.integration;

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
 * Tenant isolation on the inbox surface (multi-tenant.md M2, AC-4): a {@code wms}
 * token is rejected with 403 TENANT_FORBIDDEN; a valid {@code fan-platform} token
 * is accepted.
 */
class MultiTenantIsolationTest extends NotificationServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private ResponseEntity<String> getInbox(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange("http://localhost:" + port + "/api/fan/notifications",
                HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    @Test
    @DisplayName("a wms-tenant token → 403 (the inbox is fan-platform only)")
    void crossTenantTokenForbidden() {
        ResponseEntity<String> resp = getInbox(jwt.signCrossTenantToken("acc-x"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a valid fan-platform token → 200")
    void fanTokenAccepted() {
        ResponseEntity<String> resp = getInbox(jwt.signFanToken("acc-1"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("no token → 401")
    void noTokenUnauthorized() {
        ResponseEntity<String> resp = rest.getForEntity(
                "http://localhost:" + port + "/api/fan/notifications", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
