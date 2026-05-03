package com.example.fanplatform.community.integration;

import com.example.fanplatform.community.application.PostMediaRefSerializer;
import com.example.fanplatform.community.domain.membership.MembershipChecker;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
 * Membership-gate integration test (TASK-FAN-BE-002 § Tests § Integration —
 * {@code MembershipGateIntegrationTest}).
 *
 * <p>Replaces the default {@code AlwaysAllowMembershipChecker} with a
 * {@link DenyAllMembershipChecker} via {@link TestConfiguration} so that
 * MEMBERS_ONLY visibility actually denies access. PREMIUM v1 always passes
 * (per {@code PostAccessGuard} — TODO until v2), so we assert PREMIUM access
 * without involving the membership checker.
 */
@ContextConfiguration(classes = MembershipGateIntegrationTest.DenyMembershipConfig.class)
class MembershipGateIntegrationTest extends CommunityServiceIntegrationBase {

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

    private Post seedPublishedPost(String author, PostVisibility visibility) {
        Post p = Post.createDraft(
                UUID.randomUUID().toString(),
                "fan-platform",
                author,
                PostType.ARTIST_POST,
                visibility,
                "title",
                "body",
                mediaRefSerializer.serialize(null));
        p.publish(ActorType.AUTHOR);
        return postJpaRepository.saveAndFlush(p);
    }

    @Test
    @DisplayName("MEMBERS_ONLY post + non-member fan → 403 MEMBERSHIP_REQUIRED")
    void membersOnly_denyForNonMember() throws Exception {
        Post post = seedPublishedPost("artist-x", PostVisibility.MEMBERS_ONLY);

        String fanToken = jwt.signFanToken("fan-non-member");
        ResponseEntity<String> res = rest.exchange(
                url("/api/community/posts/" + post.getId()),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(fanToken)),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode body = objectMapper.readTree(res.getBody());
        assertThat(body.path("code").asText()).isEqualTo("MEMBERSHIP_REQUIRED");
        assertThat(body.path("details").path("requiredTier").asText()).isEqualTo("MEMBERS_ONLY");
    }

    @Test
    @DisplayName("MEMBERS_ONLY post + author fan → 200 (author bypasses membership check)")
    void membersOnly_allowForAuthor() throws Exception {
        String authorId = "artist-author";
        Post post = seedPublishedPost(authorId, PostVisibility.MEMBERS_ONLY);

        String authorToken = jwt.signArtistToken(authorId);
        ResponseEntity<String> res = rest.exchange(
                url("/api/community/posts/" + post.getId()),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(authorToken)),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(res.getBody());
        assertThat(body.path("data").path("postId").asText()).isEqualTo(post.getId());
    }

    @Test
    @DisplayName("PREMIUM post + non-member fan → 200 (v1 always-pass + WARN log; TODO v2)")
    void premium_v1AlwaysPasses() throws Exception {
        Post post = seedPublishedPost("artist-y", PostVisibility.PREMIUM);

        String fanToken = jwt.signFanToken("fan-no-premium");
        ResponseEntity<String> res = rest.exchange(
                url("/api/community/posts/" + post.getId()),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(fanToken)),
                String.class);

        // v1: PostAccessGuard logs a WARN and passes through. v2 will hard
        // fail-close via membership-service. Here we only assert the status.
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Replaces the default {@link com.example.fanplatform.community.infrastructure.membership.AlwaysAllowMembershipChecker}
     * with a checker that denies every request, simulating a non-member fan.
     */
    @TestConfiguration
    static class DenyMembershipConfig {
        @Bean
        @Primary
        MembershipChecker denyAllMembershipChecker() {
            return new DenyAllMembershipChecker();
        }
    }

    static class DenyAllMembershipChecker implements MembershipChecker {
        @Override
        public boolean hasAccess(String accountId, String tier, String tenantId) {
            return false;
        }
    }
}
