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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for TASK-FAN-BE-028: the public artist directory
 * ({@code GET /api/artists}) must return 200 when called with NO {@code q}
 * search term — the default "browse all artists" landing page.
 *
 * <p>Before the fix, the untyped {@code :q} bind made PostgreSQL resolve the
 * non-existent {@code lower(bytea)} overload for the {@code LOWER(('%'||?||'%'))}
 * subtree when {@code q} was null, so the endpoint returned 500. The
 * {@code ?q=...} (non-null) path was already exercised by
 * {@link ArtistDirectoryCacheIntegrationTest}; this class locks the null-{@code q}
 * path plus the null-{@code q} + type-filter combination. Runs on PostgreSQL
 * (Testcontainers) — H2 plans untyped nulls differently and would mask the bug.
 */
class ArtistDirectoryBrowseIntegrationTest extends ArtistServiceIntegrationBase {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/artists with no q returns 200 (browse-all), not lower(bytea) 500")
    void browseAll_noQuery_returns200() {
        String stageName = "Be028-Browse-" + System.nanoTime();
        publishArtist("SOLO", stageName);

        // No q param at all — the regression path (previously 500).
        ResponseEntity<String> res = rest.exchange(
                "/api/artists?size=200", HttpMethod.GET, new HttpEntity<>(fanHeaders()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = readData(res.getBody());
        assertThat(data.isArray()).isTrue();
        assertThat(stageNames(data)).contains(stageName);
    }

    @Test
    @DisplayName("GET /api/artists?type=SOLO with no q returns 200 (null-q + type filter still applies)")
    void browseAll_typeFilterNoQuery_returns200() {
        publishArtist("GROUP_MEMBER", "Be028-Group-" + System.nanoTime());

        ResponseEntity<String> res = rest.exchange(
                "/api/artists?type=SOLO&size=200", HttpMethod.GET, new HttpEntity<>(fanHeaders()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = readData(res.getBody());
        assertThat(data.isArray()).isTrue();
        // The type filter must still exclude non-SOLO rows on the null-q path.
        data.forEach(n -> assertThat(n.path("artistType").asText()).isEqualTo("SOLO"));
    }

    @Test
    @DisplayName("GET /api/artists?q=<term> still filters case-insensitively (non-null path preserved)")
    void search_withQuery_filters() {
        String stageName = "Be028-Search-" + System.nanoTime();
        publishArtist("SOLO", stageName);

        String q = stageName.toLowerCase(); // case-insensitive substring match
        ResponseEntity<String> res = rest.exchange(
                "/api/artists?q=" + q, HttpMethod.GET, new HttpEntity<>(fanHeaders()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = readData(res.getBody());
        assertThat(stageNames(data)).contains(stageName);
        data.forEach(n -> assertThat(n.path("stageName").asText().toLowerCase()).contains(q));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private HttpHeaders fanHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.signFanToken("fan-be028"));
        return h;
    }

    private void publishArtist(String type, String stageName) {
        HttpHeaders admin = new HttpHeaders();
        admin.setContentType(MediaType.APPLICATION_JSON);
        admin.setBearerAuth(jwt.signAdminToken("admin-be028"));
        ResponseEntity<String> reg = rest.exchange(
                "/api/artists", HttpMethod.POST,
                new HttpEntity<>("{\"artistType\":\"" + type + "\",\"stageName\":\"" + stageName + "\"}", admin),
                String.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = readData(reg.getBody()).path("id").asText();
        ResponseEntity<String> pub = rest.exchange(
                "/api/artists/" + id + "/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"PUBLISHED\"}", admin), String.class);
        assertThat(pub.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private JsonNode readData(String body) {
        try {
            return objectMapper.readTree(body).path("data");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> stageNames(JsonNode dataArray) {
        List<String> names = new ArrayList<>();
        dataArray.forEach(n -> names.add(n.path("stageName").asText()));
        return names;
    }
}
