package com.example.fanplatform.community.integration;

import com.example.fanplatform.community.application.PostMediaRefSerializer;
import com.example.fanplatform.community.domain.follow.Follow;
import com.example.fanplatform.community.domain.membership.MembershipChecker;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the PREMIUM gate in the feed query path (TASK-FAN-BE-019).
 *
 * <p>Verifies that {@code GetFeedUseCase.isLocked()} now applies fail-close
 * for PREMIUM-visibility posts — mirroring {@code PostAccessGuard} (FAN-BE-010).
 *
 * <p>Two membership checkers are provided as inner {@link TestConfiguration}
 * classes so each test class can declare its own via {@link ContextConfiguration}:
 * <ul>
 *   <li>{@link DenyMembershipConfig} — simulates a non-subscriber; locked=true.</li>
 *   <li>{@link AllowMembershipConfig} — simulates a premium subscriber; locked=false.</li>
 * </ul>
 *
 * <p>The class itself uses {@link DenyMembershipConfig}. The subscriber scenario
 * is covered by the sibling inner class {@link FeedPremiumSubscriberIT}.
 */
@ContextConfiguration(classes = FeedPremiumGateIntegrationTest.DenyMembershipConfig.class)
class FeedPremiumGateIntegrationTest extends CommunityServiceIntegrationBase {

    private static final String ARTIST   = "premium-artist";
    private static final String TENANT   = "fan-platform";

    @LocalServerPort int port;

    @Autowired TestRestTemplate       rest;
    @Autowired PostJpaRepository      postJpaRepository;
    @Autowired FollowJpaRepository    followJpaRepository;
    @Autowired PostMediaRefSerializer mediaRefSerializer;
    @Autowired ObjectMapper           objectMapper;
    @Autowired StringRedisTemplate    stringRedisTemplate;

    private String fanId;

    @BeforeEach
    void seed() {
        truncateAll();
        try {
            stringRedisTemplate.getConnectionFactory().getConnection()
                    .serverCommands().flushAll();
        } catch (RuntimeException ignored) { /* best-effort */ }

        fanId = "fan-" + UUID.randomUUID().toString().substring(0, 8);
        followJpaRepository.save(Follow.create(fanId, ARTIST, TENANT));

        Post p = Post.createDraft(
                UUID.randomUUID().toString(), TENANT, ARTIST,
                PostType.ARTIST_POST, PostVisibility.PREMIUM,
                "Premium Title", "Premium body content", mediaRefSerializer.serialize(null));
        p.publish(ActorType.AUTHOR);
        postJpaRepository.saveAndFlush(p);
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

    // -----------------------------------------------------------------------
    // AC-4: PREMIUM post in feed → locked=true for non-subscriber (deny-all)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-4: PREMIUM post in feed + non-subscriber → locked=true, title/preview null")
    void premiumPost_nonSubscriber_appearsLockedInFeed() throws Exception {
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
        assertThat(content).as("PREMIUM post from followed artist must appear in feed").hasSize(1);

        JsonNode item = content.get(0);
        assertThat(item.path("locked").asBoolean())
                .as("PREMIUM post must be locked=true for non-subscriber (fail-close)")
                .isTrue();
        assertThat(item.path("title").isNull())
                .as("locked post must not expose title")
                .isTrue();
        assertThat(item.path("bodyPreview").isNull())
                .as("locked post must not expose body preview")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // Sibling context: subscriber → locked=false
    // -----------------------------------------------------------------------

    /**
     * Separate Spring context (AllowAll checker) to verify the subscriber path:
     * a premium subscriber sees the PREMIUM post as locked=false with title
     * and preview populated.
     *
     * <p>Declared as a nested class so it shares the outer class's seed/cleanup
     * helpers but gets its own Spring application context via its own
     * {@link ContextConfiguration}.
     */
    @ContextConfiguration(classes = FeedPremiumGateIntegrationTest.AllowMembershipConfig.class)
    static class FeedPremiumSubscriberIT extends CommunityServiceIntegrationBase {

        @LocalServerPort int port;

        @Autowired TestRestTemplate       rest;
        @Autowired PostJpaRepository      postJpaRepository;
        @Autowired FollowJpaRepository    followJpaRepository;
        @Autowired PostMediaRefSerializer mediaRefSerializer;
        @Autowired ObjectMapper           objectMapper;
        @Autowired StringRedisTemplate    stringRedisTemplate;

        private String fanId;

        @BeforeEach
        void seed() {
            truncateAll();
            try {
                stringRedisTemplate.getConnectionFactory().getConnection()
                        .serverCommands().flushAll();
            } catch (RuntimeException ignored) { /* best-effort */ }

            fanId = "subscriber-" + UUID.randomUUID().toString().substring(0, 8);
            followJpaRepository.save(Follow.create(fanId, ARTIST, TENANT));

            Post p = Post.createDraft(
                    UUID.randomUUID().toString(), TENANT, ARTIST,
                    PostType.ARTIST_POST, PostVisibility.PREMIUM,
                    "Premium Title", "Premium body content",
                    mediaRefSerializer.serialize(null));
            p.publish(ActorType.AUTHOR);
            postJpaRepository.saveAndFlush(p);
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
        @DisplayName("AC-4: PREMIUM post in feed + premium subscriber → locked=false, title/preview populated")
        void premiumPost_subscriber_appearsUnlockedInFeed() throws Exception {
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
            assertThat(content).as("PREMIUM post from followed artist must appear in feed").hasSize(1);

            JsonNode item = content.get(0);
            assertThat(item.path("locked").asBoolean())
                    .as("PREMIUM post must be locked=false for a premium subscriber")
                    .isFalse();
            assertThat(item.path("title").asText())
                    .as("subscriber sees post title")
                    .isEqualTo("Premium Title");
            assertThat(item.path("bodyPreview").asText())
                    .as("subscriber sees body preview")
                    .isEqualTo("Premium body content");
        }
    }

    // -----------------------------------------------------------------------
    // TestConfiguration beans (shared by both contexts above)
    // -----------------------------------------------------------------------

    /** Deny-all checker — simulates a non-subscriber fan. */
    @TestConfiguration
    static class DenyMembershipConfig {
        @Bean
        @Primary
        MembershipChecker denyAllMembershipChecker() {
            return (accountId, tier, tenantId) -> false;
        }
    }

    /** Allow-all checker — simulates a premium subscriber. */
    @TestConfiguration
    static class AllowMembershipConfig {
        @Bean
        @Primary
        MembershipChecker allowAllMembershipChecker() {
            return (accountId, tier, tenantId) -> true;
        }
    }
}
