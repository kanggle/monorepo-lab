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

class StageNameUniquenessIntegrationTest extends ArtistServiceIntegrationBase {

    @Autowired TestRestTemplate rest;

    @Test
    @DisplayName("Same stage_name twice in same tenant → 409 STAGE_NAME_CONFLICT")
    void duplicateStageNameRejected() {
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.setBearerAuth(jwt.signAdminToken("admin-1"));
        String body = """
                {"artistType":"SOLO","stageName":"UniqueStage-1"}
                """;

        ResponseEntity<String> first = rest.exchange(
                "/api/artists", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> second = rest.exchange(
                "/api/artists", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("STAGE_NAME_CONFLICT");
    }
}
