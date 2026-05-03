package com.example.fanplatform.community.integration;

import com.example.fanplatform.community.application.PostMediaRefSerializer;
import com.example.fanplatform.community.domain.follow.Follow;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.infrastructure.jpa.FollowJpaRepository;
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
 * Contract test for {@code specs/contracts/http/community-api.md}.
 *
 * <p>Walks every documented endpoint and asserts the response shape matches
 * the spec — envelope ({@code data, meta}), required fields per resource, and
 * the error envelope shape ({@code code, message, [details], timestamp}). Not
 * exhaustive on every field — focused on contract-critical fields that the
 * frontend / event consumers depend on.
 */
class CommunityApiContractTest extends CommunityServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    PostJpaRepository postJpaRepository;

    @Autowired
    FollowJpaRepository followJpaRepository;

    @Autowired
    PostMediaRefSerializer mediaRefSerializer;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        followJpaRepository.deleteAll();
        postJpaRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        followJpaRepository.deleteAll();
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

    private Post seedPublishedPost(String author) {
        Post p = Post.createDraft(
                UUID.randomUUID().toString(),
                "fan-platform",
                author,
                PostType.ARTIST_POST,
                PostVisibility.PUBLIC,
                "title",
                "body",
                mediaRefSerializer.serialize(null));
        p.publish(ActorType.AUTHOR);
        return postJpaRepository.saveAndFlush(p);
    }

    private static void assertEnvelope(JsonNode body) {
        assertThat(body.has("data")).as("response missing 'data'").isTrue();
        assertThat(body.has("meta")).as("response missing 'meta'").isTrue();
        assertThat(body.path("meta").path("timestamp").asText())
                .as("meta.timestamp missing")
                .isNotBlank();
    }

    private static void assertErrorEnvelope(JsonNode body) {
        assertThat(body.has("code")).as("error missing 'code'").isTrue();
        assertThat(body.has("message")).as("error missing 'message'").isTrue();
        assertThat(body.has("timestamp")).as("error missing 'timestamp'").isTrue();
    }

    // ─── Posts ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/community/posts → 201 envelope with full post payload")
    void publishPost_envelopeShape() throws Exception {
        String artistId = "artist-" + UUID.randomUUID().toString().substring(0, 8);
        String token = jwt.signArtistToken(artistId);

        String body = """
                {"postType":"ARTIST_POST","visibility":"PUBLIC","title":"t","body":"b"}
                """;
        ResponseEntity<String> res = rest.exchange(
                url("/api/community/posts"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode json = objectMapper.readTree(res.getBody());
        assertEnvelope(json);

        JsonNode data = json.path("data");
        for (String key : new String[]{
                "postId", "tenantId", "postType", "visibility", "status",
                "authorAccountId", "title", "body",
                "commentCount", "reactionCount",
                "publishedAt", "createdAt", "updatedAt"}) {
            assertThat(data.has(key)).as("data missing field '%s'", key).isTrue();
        }
        assertThat(data.path("tenantId").asText()).isEqualTo("fan-platform");
        assertThat(data.path("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("GET /api/community/posts/{id} → 200 same shape as publish")
    void getPost_envelopeShape() throws Exception {
        Post post = seedPublishedPost("artist-1");
        String token = jwt.signFanToken("fan-1");

        ResponseEntity<String> res = rest.exchange(
                url("/api/community/posts/" + post.getId()),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(res.getBody());
        assertEnvelope(json);
        assertThat(json.path("data").path("postId").asText()).isEqualTo(post.getId());
    }

    @Test
    @DisplayName("GET /api/community/posts/{id} (missing) → 404 error envelope code=POST_NOT_FOUND")
    void getPost_missing_errorEnvelope() throws Exception {
        String token = jwt.signFanToken("fan-1");
        ResponseEntity<String> res = rest.exchange(
                url("/api/community/posts/nonexistent-" + UUID.randomUUID()),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode err = objectMapper.readTree(res.getBody());
        assertErrorEnvelope(err);
        assertThat(err.path("code").asText()).isEqualTo("POST_NOT_FOUND");
    }

    // ─── Feed ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/community/feed → 200 envelope with paginated content shape")
    void feed_envelopeShape() throws Exception {
        String fanId = "fan-" + UUID.randomUUID().toString().substring(0, 8);
        String token = jwt.signFanToken(fanId);

        ResponseEntity<String> res = rest.exchange(
                url("/api/community/feed?page=0&size=20"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(res.getBody());
        assertEnvelope(json);

        JsonNode data = json.path("data");
        for (String key : new String[]{
                "content", "page", "size", "totalElements", "totalPages", "hasNext"}) {
            assertThat(data.has(key)).as("feed data missing '%s'", key).isTrue();
        }
        assertThat(data.path("content").isArray()).isTrue();
    }

    // ─── Comments ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/community/posts/{postId}/comments → 201 envelope with comment payload")
    void addComment_envelopeShape() throws Exception {
        Post post = seedPublishedPost("artist-c");
        String token = jwt.signFanToken("fan-1");

        ResponseEntity<String> res = rest.exchange(
                url("/api/community/posts/" + post.getId() + "/comments"),
                HttpMethod.POST,
                new HttpEntity<>("{\"body\":\"nice\"}", authHeaders(token)),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode json = objectMapper.readTree(res.getBody());
        assertEnvelope(json);

        JsonNode data = json.path("data");
        for (String key : new String[]{
                "commentId", "postId", "tenantId", "authorAccountId", "body", "createdAt"}) {
            assertThat(data.has(key)).as("comment data missing '%s'", key).isTrue();
        }
    }

    // ─── Reactions ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/community/posts/{postId}/reactions → 200 envelope with reaction payload")
    void putReaction_envelopeShape() throws Exception {
        Post post = seedPublishedPost("artist-r");
        String token = jwt.signFanToken("fan-r");

        ResponseEntity<String> res = rest.exchange(
                url("/api/community/posts/" + post.getId() + "/reactions"),
                HttpMethod.PUT,
                new HttpEntity<>("{\"reactionType\":\"LIKE\"}", authHeaders(token)),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(res.getBody());
        assertEnvelope(json);

        JsonNode data = json.path("data");
        for (String key : new String[]{"postId", "reactionType", "totalReactions"}) {
            assertThat(data.has(key)).as("reaction data missing '%s'", key).isTrue();
        }
    }

    @Test
    @DisplayName("DELETE /api/community/posts/{postId}/reactions → 204 no body")
    void deleteReaction_noBody() {
        // Seed a published post for the path to exist.
        Post post = seedPublishedPost("artist-d");
        String token = jwt.signFanToken("fan-d");

        ResponseEntity<String> res = rest.exchange(
                url("/api/community/posts/" + post.getId() + "/reactions"),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ─── Follows ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/community/follows → 201 envelope with follow payload")
    void follow_envelopeShape() throws Exception {
        String fanId = "fan-" + UUID.randomUUID().toString().substring(0, 8);
        String artistId = "artist-" + UUID.randomUUID().toString().substring(0, 8);
        String token = jwt.signFanToken(fanId);

        String body = "{\"artistAccountId\":\"" + artistId + "\"}";

        ResponseEntity<String> res = rest.exchange(
                url("/api/community/follows"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode json = objectMapper.readTree(res.getBody());
        assertEnvelope(json);

        JsonNode data = json.path("data");
        for (String key : new String[]{
                "fanAccountId", "artistAccountId", "tenantId", "followedAt"}) {
            assertThat(data.has(key)).as("follow data missing '%s'", key).isTrue();
        }
    }

    @Test
    @DisplayName("POST /api/community/follows (self-follow) → 422 SELF_FOLLOW_FORBIDDEN error envelope")
    void follow_self_errorEnvelope() throws Exception {
        String fanId = "fan-self-" + UUID.randomUUID().toString().substring(0, 8);
        String token = jwt.signFanToken(fanId);

        String body = "{\"artistAccountId\":\"" + fanId + "\"}";
        ResponseEntity<String> res = rest.exchange(
                url("/api/community/follows"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode err = objectMapper.readTree(res.getBody());
        assertErrorEnvelope(err);
        assertThat(err.path("code").asText()).isEqualTo("SELF_FOLLOW_FORBIDDEN");
    }

    // ─── Auth / Tenant gating envelopes ────────────────────────────────────────

    @Test
    @DisplayName("(no Authorization) → 401 error envelope with code")
    void unauthenticated_errorEnvelope() throws Exception {
        ResponseEntity<String> res = rest.exchange(
                url("/api/community/feed"),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode err = objectMapper.readTree(res.getBody());
        assertErrorEnvelope(err);
    }

    @Test
    @DisplayName("(tenant_id=wms token) → 403 TENANT_FORBIDDEN error envelope")
    void crossTenant_errorEnvelope() throws Exception {
        String token = jwt.signCrossTenantToken("operator-1");

        ResponseEntity<String> res = rest.exchange(
                url("/api/community/feed"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode err = objectMapper.readTree(res.getBody());
        assertErrorEnvelope(err);
        assertThat(err.path("code").asText()).isEqualTo("TENANT_FORBIDDEN");
    }
}
