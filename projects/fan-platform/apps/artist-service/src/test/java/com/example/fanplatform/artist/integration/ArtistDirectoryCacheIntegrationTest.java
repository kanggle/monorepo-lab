package com.example.fanplatform.artist.integration;

import com.example.fanplatform.artist.application.port.out.ArtistDirectoryCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that:
 * <ul>
 *   <li>a directory query populates Redis (cache write-through);</li>
 *   <li>publishing a new artist invalidates the tenant's cache entries.</li>
 * </ul>
 */
class ArtistDirectoryCacheIntegrationTest extends ArtistServiceIntegrationBase {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper objectMapper;
    @Autowired StringRedisTemplate redis;
    @Autowired ArtistDirectoryCache cache;

    @Test
    @DisplayName("directory search populates cache; publish invalidates it")
    void cachePopulationAndInvalidation() {
        HttpHeaders fanHeaders = new HttpHeaders();
        fanHeaders.setBearerAuth(jwt.signFanToken("fan-1"));

        // 1) Search → populates cache
        ResponseEntity<String> first = rest.exchange(
                "/api/artists?q=cachetest", HttpMethod.GET,
                new HttpEntity<>(fanHeaders), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        Set<String> keys = redis.keys("cache:fan-platform:artist:directory:fan-platform:*");
        assertThat(keys).isNotNull().isNotEmpty();

        // 2) Register + publish a new artist as admin
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.setBearerAuth(jwt.signAdminToken("admin-1"));
        String body = """
                {"artistType":"SOLO","stageName":"CacheInvTest-1"}
                """;
        ResponseEntity<String> reg = rest.exchange(
                "/api/artists", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders), String.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = extractId(reg.getBody());

        ResponseEntity<String> pub = rest.exchange(
                "/api/artists/" + id + "/status", HttpMethod.PATCH,
                new HttpEntity<>("""
                        {"status":"PUBLISHED"}
                        """, adminHeaders), String.class);
        assertThat(pub.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3) Cache for the tenant should be cleared. redis.keys() may return
        //    null or an empty set depending on the Redis client config — both
        //    indicate "no keys remain", which is what we want.
        Set<String> keysAfter = redis.keys("cache:fan-platform:artist:directory:fan-platform:*");
        assertThat(keysAfter == null || keysAfter.isEmpty())
                .as("cache should be invalidated after publish")
                .isTrue();
    }

    private String extractId(String body) {
        try {
            return objectMapper.readTree(body).path("data").path("id").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
