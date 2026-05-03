package com.example.fanplatform.artist.integration;

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

/**
 * Contract checks: unauthenticated GETs/POSTs return 401 with the {@code
 * UNAUTHORIZED} envelope, fan-role POSTs return 403 with the {@code FORBIDDEN}
 * envelope. These are the load-bearing bits of {@code
 * specs/contracts/http/artist-api.md} that don't depend on data being present.
 */
class ArtistApiContractTest extends ArtistServiceIntegrationBase {

    @Autowired TestRestTemplate rest;

    @Test
    @DisplayName("GET /api/artists (no auth) → 401 envelope")
    void unauthenticatedGetReturns401Envelope() {
        ResponseEntity<String> response = rest.getForEntity("/api/artists", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    @DisplayName("POST /api/artists (FAN role) → 403 FORBIDDEN")
    void fanPostReturns403() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt.signFanToken("fan-1"));
        String body = """
                {"artistType":"SOLO","stageName":"STAGE-X"}
                """;
        ResponseEntity<String> response = rest.exchange(
                "/api/artists", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    @DisplayName("GET /api/artists/{nonexistent} (admin) → 404 ARTIST_NOT_FOUND")
    void adminGetMissingReturns404() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.signAdminToken("admin-1"));
        ResponseEntity<String> response = rest.exchange(
                "/api/artists/00000000-0000-0000-0000-000000000000", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"ARTIST_NOT_FOUND\"");
    }
}
