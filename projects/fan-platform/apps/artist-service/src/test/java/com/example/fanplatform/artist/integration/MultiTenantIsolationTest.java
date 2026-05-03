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

class MultiTenantIsolationTest extends ArtistServiceIntegrationBase {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("cross-tenant token (tenant_id=wms) → 403 TENANT_FORBIDDEN")
    void crossTenantBlocked() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.signCrossTenantToken("op-1"));
        ResponseEntity<String> response = rest.exchange(
                "/api/artists", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("TENANT_FORBIDDEN");
    }

    @Test
    @DisplayName("admin can register; same admin can GET its own DRAFT; FAN sees 404")
    void adminCreatesAdminGetsDraftFanGets404() throws Exception {
        // Register a DRAFT artist
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.setBearerAuth(jwt.signAdminToken("admin-1"));
        String body = """
                {"artistType":"SOLO","stageName":"DraftHidden-1"}
                """;
        ResponseEntity<String> registerResp = rest.exchange(
                "/api/artists", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders), String.class);
        assertThat(registerResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode root = objectMapper.readTree(registerResp.getBody());
        String artistId = root.path("data").path("id").asText();

        // Admin can see the DRAFT
        ResponseEntity<String> adminGet = rest.exchange(
                "/api/artists/" + artistId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders), String.class);
        assertThat(adminGet.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Fan cannot — gets 404 (do not leak existence)
        HttpHeaders fanHeaders = new HttpHeaders();
        fanHeaders.setBearerAuth(jwt.signFanToken("fan-1"));
        ResponseEntity<String> fanGet = rest.exchange(
                "/api/artists/" + artistId, HttpMethod.GET,
                new HttpEntity<>(fanHeaders), String.class);
        assertThat(fanGet.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(fanGet.getBody()).contains("ARTIST_NOT_FOUND");
    }
}
