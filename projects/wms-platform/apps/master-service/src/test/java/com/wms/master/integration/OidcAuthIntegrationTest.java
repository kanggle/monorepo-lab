package com.wms.master.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-019 regression tests covering the OIDC migration acceptance criteria
 * for master-service:
 *
 * <ul>
 *   <li>Legacy {@code POST /api/auth/login} tokens
 *       (iss=global-account-platform) still pass — D2-b deprecation
 *       compatibility.</li>
 *   <li>SAS-issued tokens (iss=oidc.issuer-url) pass — primary path.</li>
 *   <li>Cross-tenant tokens (tenant_id=fan-platform) are rejected with
 *       403 TENANT_FORBIDDEN.</li>
 *   <li>Missing Authorization header returns 401.</li>
 * </ul>
 */
@Tag("integration")
@DisplayName("TASK-MONO-019 OIDC 인증 회귀 통합 테스트 (master-service)")
class OidcAuthIntegrationTest extends MasterServiceIntegrationBase {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("legacy iss=global-account-platform 토큰 → 200 (호환성)")
    void legacyToken_returns200() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JWT.issueToken("integration-actor", "MASTER_READ"));
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/master/warehouses", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("SAS issuer 토큰 → 200 (표준 경로)")
    void sasToken_returns200() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JWT.issueSasToken("integration-actor", List.of("MASTER_READ")));
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/master/warehouses", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("cross-tenant 토큰 (tenant_id=fan-platform) → 403 TENANT_FORBIDDEN")
    void crossTenantFanPlatformToken_returns403() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JWT.issueTokenWithTenant(
                "fan-actor-" + UUID.randomUUID(),
                List.of("MASTER_READ"),
                "fan-platform"));
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/master/warehouses", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("error").path("code").asText()).isEqualTo("TENANT_FORBIDDEN");
    }

    @Test
    @DisplayName("Authorization 헤더 없음 → 401")
    void missingAuthorization_returns401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/master/warehouses", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
