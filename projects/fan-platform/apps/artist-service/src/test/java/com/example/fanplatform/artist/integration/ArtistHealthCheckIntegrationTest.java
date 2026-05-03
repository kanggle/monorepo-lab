package com.example.fanplatform.artist.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistHealthCheckIntegrationTest extends ArtistServiceIntegrationBase {

    @Autowired TestRestTemplate rest;

    @Test
    @DisplayName("/actuator/health → 200 unauthenticated")
    void healthIsPublicAndOk() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }
}
