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

/**
 * Happy-path E2E: register an artist (admin), publish, then fan can see it
 * via search and via GET. Mirrors community-service's E2E pattern.
 */
class ArtistServiceIntegrationTest extends ArtistServiceIntegrationBase {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("register → publish → directory search returns published artist")
    void happyPath() throws Exception {
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.setBearerAuth(jwt.signAdminToken("admin-1"));

        // 1) register
        String registerBody = """
                {"artistType":"SOLO","stageName":"E2ETest-1","agency":"AgencyZ"}
                """;
        ResponseEntity<String> registerResp = rest.exchange(
                "/api/artists", HttpMethod.POST,
                new HttpEntity<>(registerBody, adminHeaders), String.class);
        assertThat(registerResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode root = objectMapper.readTree(registerResp.getBody());
        String artistId = root.path("data").path("id").asText();

        // 2) publish
        String statusBody = """
                {"status":"PUBLISHED"}
                """;
        ResponseEntity<String> publishResp = rest.exchange(
                "/api/artists/" + artistId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(statusBody, adminHeaders), String.class);
        assertThat(publishResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode publishRoot = objectMapper.readTree(publishResp.getBody());
        assertThat(publishRoot.path("data").path("status").asText()).isEqualTo("PUBLISHED");

        // 3) fan can GET the published artist
        HttpHeaders fanHeaders = new HttpHeaders();
        fanHeaders.setBearerAuth(jwt.signFanToken("fan-1"));
        ResponseEntity<String> fanGet = rest.exchange(
                "/api/artists/" + artistId, HttpMethod.GET,
                new HttpEntity<>(fanHeaders), String.class);
        assertThat(fanGet.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 4) directory search finds it
        ResponseEntity<String> search = rest.exchange(
                "/api/artists?q=E2ETest", HttpMethod.GET,
                new HttpEntity<>(fanHeaders), String.class);
        assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode searchRoot = objectMapper.readTree(search.getBody());
        assertThat(searchRoot.path("data").isArray()).isTrue();
        assertThat(searchRoot.path("data").size()).isGreaterThanOrEqualTo(1);
    }
}
