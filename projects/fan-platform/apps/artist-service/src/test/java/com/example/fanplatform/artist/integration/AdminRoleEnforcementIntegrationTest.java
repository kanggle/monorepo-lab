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
}
