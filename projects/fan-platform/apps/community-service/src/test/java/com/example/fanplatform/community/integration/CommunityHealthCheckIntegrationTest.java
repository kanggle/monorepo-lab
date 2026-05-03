package com.example.fanplatform.community.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke integration test:
 * <ul>
 *   <li>{@code /actuator/health} responds 200 unauthenticated.</li>
 *   <li>An unauthenticated call to {@code /api/community/posts} responds 401.</li>
 * </ul>
 *
 * <p>Together these prove the application starts with the full filter chain,
 * Postgres + Kafka + Redis are reachable, and Flyway migrated successfully.
 */
class CommunityHealthCheckIntegrationTest extends CommunityServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    @DisplayName("/actuator/health → 200 (unauthenticated)")
    void healthIsPublic() {
        ResponseEntity<String> r = rest.getForEntity("http://localhost:" + port + "/actuator/health", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("/api/community/posts (no JWT) → 401 UNAUTHORIZED")
    void protectedRouteRequiresAuth() {
        ResponseEntity<String> r = rest.getForEntity("http://localhost:" + port + "/api/community/posts/00000000-0000-0000-0000-000000000000", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
