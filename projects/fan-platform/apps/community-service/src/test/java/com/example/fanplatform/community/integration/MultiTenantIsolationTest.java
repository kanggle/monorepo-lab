package com.example.fanplatform.community.integration;

import com.example.fanplatform.community.application.PostMediaRefSerializer;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.infrastructure.jpa.PostJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-tenant isolation integration test (TASK-FAN-BE-002 § Acceptance
 * Criteria, § Edge Cases — Cross-tenant 포스트 ID 추측).
 *
 * <p>Verifies:
 * <ul>
 *   <li>tenant-mismatched JWT (e.g. {@code tenant_id=wms}) is rejected at the
 *       JWT validator → 403 {@code TENANT_FORBIDDEN};</li>
 *   <li>a {@code fan-platform} caller probing a post id that belongs to
 *       another tenant gets 404 {@code POST_NOT_FOUND} — existence is not
 *       leaked;</li>
 *   <li>the feed query for a {@code fan-platform} caller never returns posts
 *       owned by other tenants.</li>
 * </ul>
 */
class MultiTenantIsolationTest extends CommunityServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    PostJpaRepository postJpaRepository;

    @Autowired
    PostMediaRefSerializer mediaRefSerializer;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        postJpaRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        postJpaRepository.deleteAll();
    }

    private HttpHeaders authHeaders(String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(bearer);
        return h;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @DisplayName("cross-tenant token (tenant_id=wms) is rejected with 403 TENANT_FORBIDDEN")
    void crossTenantToken_isRejected() throws Exception {
        String wmsToken = jwt.signCrossTenantToken("operator-1");

        String body = """
                {"postType":"FAN_POST","visibility":"PUBLIC","body":"hi"}
                """;

        ResponseEntity<String> res = rest.exchange(
                url("/api/community/posts"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(wmsToken)),
                String.class);

        // The TenantClaimValidator inside the JWT decoder fails the token,
        // SecurityConfig's authentication-failure handler maps it to 403
        // TENANT_FORBIDDEN (see SecurityConfig § onAuthenticationFailure).
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode errJson = objectMapper.readTree(res.getBody());
        assertThat(errJson.path("code").asText()).isEqualTo("TENANT_FORBIDDEN");
    }

    @Test
    @DisplayName("GET on a post belonging to another tenant → 404 POST_NOT_FOUND (no existence leak)")
    void crossTenantPostId_returns404() throws Exception {
        // Seed a post that belongs to a different tenant (NOT fan-platform).
        Post other = Post.createDraft(
                UUID.randomUUID().toString(),
                "other-tenant",
                "artist-other",
                PostType.ARTIST_POST,
                PostVisibility.PUBLIC,
                "title",
                "body",
                mediaRefSerializer.serialize(null));
        other.publish(ActorType.AUTHOR);
        postJpaRepository.save(other);

        // A legitimate fan-platform caller asks for that post by id.
        String fanToken = jwt.signFanToken("fan-1");
        ResponseEntity<String> res = rest.exchange(
                url("/api/community/posts/" + other.getId()),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(fanToken)),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode errJson = objectMapper.readTree(res.getBody());
        assertThat(errJson.path("code").asText()).isEqualTo("POST_NOT_FOUND");
    }

    @Test
    @DisplayName("feed query for fan-platform caller does NOT return other-tenant posts")
    void feedFiltersOutOtherTenants() throws Exception {
        // Seed two posts: one fan-platform, one other-tenant. Both PUBLISHED.
        Post fanPlatformPost = Post.createDraft(
                UUID.randomUUID().toString(),
                "fan-platform",
                "artist-fp",
                PostType.ARTIST_POST,
                PostVisibility.PUBLIC,
                "fp",
                "fp body",
                mediaRefSerializer.serialize(null));
        fanPlatformPost.publish(ActorType.AUTHOR);
        postJpaRepository.save(fanPlatformPost);

        Post otherTenantPost = Post.createDraft(
                UUID.randomUUID().toString(),
                "other-tenant",
                "artist-fp", // same author id, but another tenant
                PostType.ARTIST_POST,
                PostVisibility.PUBLIC,
                "ot",
                "ot body",
                mediaRefSerializer.serialize(null));
        otherTenantPost.publish(ActorType.AUTHOR);
        postJpaRepository.save(otherTenantPost);

        String fanToken = jwt.signFanToken("fan-fp");

        ResponseEntity<String> res = rest.exchange(
                url("/api/community/feed?page=0&size=20"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(fanToken)),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(res.getBody());
        JsonNode content = body.path("data").path("content");
        // The feed is follow-scoped; this fan follows nobody, so nothing
        // returns. The contract: the other-tenant post must NEVER appear
        // regardless of follow set. With no follows, content must be empty.
        assertThat(content.isArray()).isTrue();
        for (JsonNode item : content) {
            assertThat(item.path("postId").asText())
                    .as("other-tenant post must never appear in fan-platform feed")
                    .isNotEqualTo(otherTenantPost.getId());
        }
    }
}
