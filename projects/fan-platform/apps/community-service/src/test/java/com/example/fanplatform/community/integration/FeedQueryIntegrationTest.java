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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feed query integration test (TASK-FAN-BE-002 § Tests § Integration —
 * {@code FeedQueryIntegrationTest}).
 *
 * <p>Seeds 30 posts split between followed artists and non-followed authors.
 * Verifies pagination (page=0/size=10 → 10 items; page=1/size=10 → next 10),
 * follow-scope filtering (non-followed authors never appear), and that the
 * Redis cache key for the fan exists after the first query (read-through write
 * via {@code FeedCacheRepository}).
 */
class FeedQueryIntegrationTest extends CommunityServiceIntegrationBase {

    private static final int TOTAL_FOLLOWED_POSTS = 25;
    private static final int TOTAL_OTHER_AUTHOR_POSTS = 5;

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

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    private String fanId;
    private String artistA;
    private String artistB;

    @BeforeEach
    void seed() {
        followJpaRepository.deleteAll();
        postJpaRepository.deleteAll();

        // Make redis state clean.
        try {
            stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        } catch (RuntimeException ignored) {
            // best-effort
        }

        fanId = "fan-" + UUID.randomUUID().toString().substring(0, 8);
        artistA = "artistA-" + UUID.randomUUID().toString().substring(0, 8);
        artistB = "artistB-" + UUID.randomUUID().toString().substring(0, 8);
        String otherAuthor = "other-" + UUID.randomUUID().toString().substring(0, 8);

        // Fan follows artistA and artistB only.
        followJpaRepository.save(Follow.create(fanId, artistA, "fan-platform"));
        followJpaRepository.save(Follow.create(fanId, artistB, "fan-platform"));

        // Seed posts. publishedAt is staggered so order-by DESC is deterministic.
        Instant base = Instant.now().minusSeconds(60_000);
        int total = 0;
        for (int i = 0; i < TOTAL_FOLLOWED_POSTS; i++) {
            String author = (i % 2 == 0) ? artistA : artistB;
            persistPublishedPost(author, base.plusSeconds(i));
            total++;
        }
        for (int i = 0; i < TOTAL_OTHER_AUTHOR_POSTS; i++) {
            persistPublishedPost(otherAuthor, base.plusSeconds(total + i));
        }
    }

    @AfterEach
    void cleanUp() {
        followJpaRepository.deleteAll();
        postJpaRepository.deleteAll();
    }

    private void persistPublishedPost(String author, Instant publishedAt) {
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
        // Force the publishedAt for deterministic ordering in tests.
        forcePublishedAt(p, publishedAt);
        postJpaRepository.saveAndFlush(p);
    }

    private static void forcePublishedAt(Post p, Instant publishedAt) {
        try {
            Field f = Post.class.getDeclaredField("publishedAt");
            f.setAccessible(true);
            f.set(p, publishedAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to backdate publishedAt for test", e);
        }
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
    @DisplayName("page=0&size=10 → 10 followed posts ordered by publishedAt DESC; non-followed authors absent")
    void firstPage_returnsTen_followedOnly() throws Exception {
        String fanToken = jwt.signFanToken(fanId);

        ResponseEntity<String> res = rest.exchange(
                url("/api/community/feed?page=0&size=10"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(fanToken)),
                String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(res.getBody());
        JsonNode content = body.path("data").path("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(10);
        assertThat(body.path("data").path("totalElements").asLong())
                .isEqualTo(TOTAL_FOLLOWED_POSTS);
        assertThat(body.path("data").path("hasNext").asBoolean()).isTrue();

        // Every item is by a followed author.
        for (JsonNode item : content) {
            String author = item.path("authorAccountId").asText();
            assertThat(author)
                    .as("non-followed author leaked into feed")
                    .isIn(artistA, artistB);
        }

        // Order: publishedAt DESC — the first item must have the largest
        // publishedAt of any followed post (i.e. seq index 24 in the seed loop).
        Instant first = Instant.parse(content.get(0).path("publishedAt").asText());
        Instant last = Instant.parse(content.get(content.size() - 1).path("publishedAt").asText());
        assertThat(first).isAfterOrEqualTo(last);
    }

    @Test
    @DisplayName("page=1&size=10 → next 10 followed posts (no overlap with page=0)")
    void secondPage_returnsNextTen() throws Exception {
        String fanToken = jwt.signFanToken(fanId);

        JsonNode page0 = fetchFeed(fanToken, 0, 10);
        JsonNode page1 = fetchFeed(fanToken, 1, 10);

        assertThat(page0.path("data").path("content").size()).isEqualTo(10);
        assertThat(page1.path("data").path("content").size()).isEqualTo(10);

        // No id appears in both pages.
        for (JsonNode a : page0.path("data").path("content")) {
            for (JsonNode b : page1.path("data").path("content")) {
                assertThat(a.path("postId").asText())
                        .isNotEqualTo(b.path("postId").asText());
            }
        }
    }

    @Test
    @DisplayName("after first feed query, Redis cache key for the page exists")
    void firstQuery_writesRedisCache() throws Exception {
        String fanToken = jwt.signFanToken(fanId);

        // Trigger a feed call (size=10).
        fetchFeed(fanToken, 0, 10);

        // FeedCacheRepository key shape: feed:<tenant>:<account>:<page>:<size>.
        String key = "feed:fan-platform:" + fanId + ":0:10";
        Boolean exists = stringRedisTemplate.hasKey(key);
        assertThat(exists)
                .as("Redis cache key %s must be present after feed query", key)
                .isTrue();

        String cached = stringRedisTemplate.opsForValue().get(key);
        assertThat(cached)
                .as("Cached value should be the JSON-serialized FeedPage")
                .isNotBlank()
                .as("Cached payload should be JSON (start with '{')")
                .startsWith("{");
    }

    @Test
    @DisplayName("read-through: 2nd call hits Redis (DB mutation invisible until TTL)")
    void secondCall_servedFromCacheNotDb() throws Exception {
        String fanToken = jwt.signFanToken(fanId);
        String key = "feed:fan-platform:" + fanId + ":0:10";

        // 1) First call: cache miss. Verify the key was absent before, and
        //    the cache key exists after.
        assertThat(Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))).isFalse();

        JsonNode first = fetchFeed(fanToken, 0, 10);
        int firstSize = first.path("data").path("content").size();
        long firstTotal = first.path("data").path("totalElements").asLong();
        assertThat(firstSize).isEqualTo(10);

        assertThat(Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))).isTrue();

        // 2) Mutate the DB AFTER the first call: delete every followed-author
        //    post. If the second call queries Postgres, totalElements drops to
        //    0; if it serves from Redis, totalElements MUST still match the
        //    pre-mutation snapshot.
        postJpaRepository.deleteAll();
        assertThat(postJpaRepository.count()).isZero();

        JsonNode second = fetchFeed(fanToken, 0, 10);
        long secondTotal = second.path("data").path("totalElements").asLong();
        int secondSize = second.path("data").path("content").size();

        assertThat(secondTotal)
                .as("read-through cache must serve the PRE-mutation snapshot — proves no DB hit")
                .isEqualTo(firstTotal);
        assertThat(secondSize)
                .as("cached page content must match the first call exactly")
                .isEqualTo(firstSize);
    }

    private JsonNode fetchFeed(String token, int page, int size) throws Exception {
        ResponseEntity<String> res = rest.exchange(
                url("/api/community/feed?page=" + page + "&size=" + size),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(res.getBody());
    }
}
