package com.example.fanplatform.community.integration;

import com.example.fanplatform.community.domain.follow.Follow;
import com.example.fanplatform.community.infrastructure.jpa.FollowJpaRepository;
import com.example.fanplatform.community.infrastructure.jpa.PostJpaRepository;
import com.example.messaging.outbox.OutboxJpaEntity;
import com.example.messaging.outbox.OutboxJpaRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end happy-path integration test (TASK-FAN-BE-002 § Tests § Integration).
 *
 * <p>Covers all five controllers in a single flow: publish post → add comment →
 * upsert reaction (twice — verify idempotent) → query feed. Asserts persistence
 * (posts, comments, reactions, follows tables) and outbox enqueue.
 */
class CommunityServiceIntegrationTest extends CommunityServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    PostJpaRepository postJpaRepository;

    @Autowired
    FollowJpaRepository followJpaRepository;

    @Autowired
    OutboxJpaRepository outboxJpaRepository;

    @Autowired
    ObjectMapper objectMapper;

    private String artistId;
    private String fanId;

    @BeforeEach
    void seedFollow() {
        // Each test starts from a clean slate so feed queries are deterministic.
        // JDBC TRUNCATE (not repo deleteAll) — the outbox repo has no default tx
        // (memory §19c).
        truncateAll();

        artistId = "artist-" + System.nanoTime();
        fanId = "fan-" + System.nanoTime();
        followJpaRepository.save(Follow.create(fanId, artistId, "fan-platform"));
    }

    @AfterEach
    void cleanUp() {
        truncateAll();
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
    @DisplayName("E2E happy path — publish → comment → react (twice) → feed → outbox enqueued")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    void e2eHappyPath() throws Exception {
        String artistToken = jwt.signArtistToken(artistId);
        String fanToken = jwt.signFanToken(fanId);

        // 1) artist publishes a PUBLIC post -------------------------------------
        String publishBody = """
                {"postType":"ARTIST_POST","visibility":"PUBLIC","title":"Hello","body":"World"}
                """;
        ResponseEntity<String> publishRes = rest.exchange(
                url("/api/community/posts"),
                HttpMethod.POST,
                new HttpEntity<>(publishBody, authHeaders(artistToken)),
                String.class);
        assertThat(publishRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode publishJson = objectMapper.readTree(publishRes.getBody());
        assertThat(publishJson.has("data")).isTrue();
        assertThat(publishJson.has("meta")).isTrue();
        String postId = publishJson.path("data").path("postId").asText();
        assertThat(postId).isNotBlank();
        assertThat(publishJson.path("data").path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(publishJson.path("data").path("tenantId").asText()).isEqualTo("fan-platform");

        // posts row exists
        assertThat(postJpaRepository.findByIdAndTenantId(postId, "fan-platform")).isPresent();

        // outbox row exists for community.post.published
        List<OutboxJpaEntity> postOutbox = outboxJpaRepository.findAll().stream()
                .filter(e -> postId.equals(e.getAggregateId()))
                .toList();
        assertThat(postOutbox).isNotEmpty();
        assertThat(postOutbox).anyMatch(e -> "community.post.published".equals(e.getEventType()));

        // 2) fan adds a comment --------------------------------------------------
        String commentBody = """
                {"body":"Great post!"}
                """;
        ResponseEntity<String> commentRes = rest.exchange(
                url("/api/community/posts/" + postId + "/comments"),
                HttpMethod.POST,
                new HttpEntity<>(commentBody, authHeaders(fanToken)),
                String.class);
        assertThat(commentRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode commentJson = objectMapper.readTree(commentRes.getBody());
        assertThat(commentJson.path("data").path("body").asText()).isEqualTo("Great post!");
        assertThat(commentJson.path("data").path("postId").asText()).isEqualTo(postId);

        // 3) fan PUTs a LIKE reaction (first call) -------------------------------
        String reactionBody = """
                {"reactionType":"LIKE"}
                """;
        ResponseEntity<String> r1 = rest.exchange(
                url("/api/community/posts/" + postId + "/reactions"),
                HttpMethod.PUT,
                new HttpEntity<>(reactionBody, authHeaders(fanToken)),
                String.class);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode r1json = objectMapper.readTree(r1.getBody());
        assertThat(r1json.path("data").path("reactionType").asText()).isEqualTo("LIKE");
        assertThat(r1json.path("data").path("totalReactions").asLong()).isEqualTo(1L);

        // 4) fan PUTs the same reaction (idempotent — still 1 row, totalReactions=1)
        ResponseEntity<String> r2 = rest.exchange(
                url("/api/community/posts/" + postId + "/reactions"),
                HttpMethod.PUT,
                new HttpEntity<>(reactionBody, authHeaders(fanToken)),
                String.class);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode r2json = objectMapper.readTree(r2.getBody());
        assertThat(r2json.path("data").path("totalReactions").asLong())
                .as("second identical PUT must not duplicate the row")
                .isEqualTo(1L);

        // 5) fan reads their feed ------------------------------------------------
        ResponseEntity<String> feedRes = rest.exchange(
                url("/api/community/feed?page=0&size=20"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(fanToken)),
                String.class);
        assertThat(feedRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode feedJson = objectMapper.readTree(feedRes.getBody());
        assertThat(feedJson.path("data").path("content").isArray()).isTrue();
        assertThat(feedJson.path("data").path("totalElements").asLong()).isEqualTo(1L);
        assertThat(feedJson.path("data").path("content").get(0).path("postId").asText())
                .isEqualTo(postId);
    }

    @Test
    @DisplayName("E2E follow → unfollow flow")
    void followAndUnfollow() {
        String fanToken = jwt.signFanToken(fanId);
        String otherArtist = "artist-other-" + System.nanoTime();

        // Follow
        String followBody = "{\"artistAccountId\":\"" + otherArtist + "\"}";
        ResponseEntity<String> followRes = rest.exchange(
                url("/api/community/follows"),
                HttpMethod.POST,
                new HttpEntity<>(followBody, authHeaders(fanToken)),
                String.class);
        assertThat(followRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(followJpaRepository
                .findByFanAccountIdAndArtistAccountIdAndTenantId(fanId, otherArtist, "fan-platform"))
                .isPresent();

        // Unfollow
        ResponseEntity<String> unfollowRes = rest.exchange(
                url("/api/community/follows/" + otherArtist),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(fanToken)),
                String.class);
        assertThat(unfollowRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(followJpaRepository
                .findByFanAccountIdAndArtistAccountIdAndTenantId(fanId, otherArtist, "fan-platform"))
                .isEmpty();
    }
}
