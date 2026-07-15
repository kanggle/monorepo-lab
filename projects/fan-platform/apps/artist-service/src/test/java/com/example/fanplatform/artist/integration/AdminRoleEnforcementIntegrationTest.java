package com.example.fanplatform.artist.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRoleEnforcementIntegrationTest extends ArtistServiceIntegrationBase {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("fan token POST /api/artists → 403; admin token POST → 201")
    void fanForbiddenAdminCreated() throws Exception {
        // FAN: 403
        HttpHeaders fanHeaders = new HttpHeaders();
        fanHeaders.setContentType(MediaType.APPLICATION_JSON);
        fanHeaders.setBearerAuth(jwt.signFanToken("fan-1"));
        String body = """
                {"artistType":"SOLO","stageName":"AdminTest-1"}
                """;
        ResponseEntity<String> fanResponse = rest.exchange(
                "/api/artists", HttpMethod.POST,
                new HttpEntity<>(body, fanHeaders), String.class);
        assertThat(fanResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // ADMIN: 201
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.setBearerAuth(jwt.signAdminToken("admin-1"));
        ResponseEntity<String> adminResponse = rest.exchange(
                "/api/artists", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders), String.class);
        assertThat(adminResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode root = objectMapper.readTree(adminResponse.getBody());
        assertThat(root.path("data").path("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("assume-tenant FAN_OPERATOR token POST /api/artists → 201 (TASK-MONO-417)")
    void assumeTenantOperatorCreated() throws Exception {
        // iam mints FAN_OPERATOR (not a generic role) on the assume-tenant token-exchange.
        // A cross-tenant console operator carrying only FAN_OPERATOR must be admitted on the
        // admin-tier mutating routes, not 403'd. Before TASK-MONO-417 ADMIN_ROLES held only the
        // generic triple, so this returned 403 — a silent privilege downgrade at the service.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt.signFanOperatorToken("assume-op-1"));
        String body = """
                {"artistType":"SOLO","stageName":"AssumeTenantOp-1"}
                """;
        ResponseEntity<String> response = rest.exchange(
                "/api/artists", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
