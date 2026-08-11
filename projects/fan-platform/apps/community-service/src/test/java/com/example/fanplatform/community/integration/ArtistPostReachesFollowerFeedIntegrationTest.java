package com.example.fanplatform.community.integration;

import com.example.fanplatform.community.domain.follow.ArtistAccountChecker;
import com.example.fanplatform.community.domain.follow.Follow;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-FAN-BE-045 <strong>AC-2 + AC-3 in one method</strong> — the join the ticket
 * exists for.
 *
 * <h2>Why one method and not three</h2>
 *
 * Publishing was already green (an ARTIST-role caller gets 201) and the feed was
 * already green (a follower sees followed authors' posts). Both stayed green while
 * <em>no caller existed who could produce a post the feed would ever surface</em>,
 * because nothing asserted that the author id a real publish writes
 * ({@code posts.author_account_id}) and the target a real follow writes
 * ({@code follows.artist_account_id}) live in the same id space. Splitting the two
 * halves across methods reproduces exactly that blind spot: each half can pass
 * against a different artist id. So the publish, the follow, the positive feed read
 * and the negative feed read are one method, driven through the HTTP API, with no
 * value hand-seeded into either table.
 *
 * <p>AC-3's negative control is in the same method for the same reason: a positive
 * assertion alone cannot distinguish "the join works" from "the feed shows
 * everything to everyone".
 *
 * <h2>The one stub, and why it is sanctioned</h2>
 *
 * {@link ArtistAccountChecker} is overridden with a {@code @Primary} test bean
 * because artist-service is a different service with a different database and is
 * not in this suite's compose. That seam is the one {@code ArtistAccountCheckerConfig}
 * explicitly sanctions for tests. It is a whitelist, not an always-allow: any target
 * not registered in {@link ConfirmingArtistAccountConfig#CONFIRMED} is denied, so the
 * checker still discriminates here. The <em>real</em> adapter (including the
 * fail-closed behaviour) is exercised by {@link FollowArtistGateIntegrationTest},
 * which deliberately does NOT stub this port.
 */
@ContextConfiguration(classes = ArtistPostReachesFollowerFeedIntegrationTest.ConfirmingArtistAccountConfig.class)
class ArtistPostReachesFollowerFeedIntegrationTest extends CommunityServiceIntegrationBase {

    private static final String TENANT = "fan-platform";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    FollowJpaRepository followJpaRepository;

    @Autowired
    PostJpaRepository postJpaRepository;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        truncateAll();
        ConfirmingArtistAccountConfig.CONFIRMED.clear();
    }

    @AfterEach
    void cleanUp() {
        truncateAll();
        ConfirmingArtistAccountConfig.CONFIRMED.clear();
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
    @DisplayName("AC-2+AC-3: ARTIST 역할 호출자가 발행한 ARTIST_POST 가 그 아티스트를 팔로우한 팬의 피드에 뜨고, "
            + "팔로우하지 않은 팬의 피드에는 같은 테스트 안에서 뜨지 않는다")
    void artistPostPublishedByRealCaller_reachesFollowerFeed_andIsAbsentFromNonFollowerFeed() throws Exception {
        // 🔴 Bare UUIDs (36 chars), NOT a readable prefix + UUID. Account ids are
        // VARCHAR(36) throughout this schema and FollowArtistRequest enforces
        // @Size(max = 36), so "artist-acct-<uuid>" (48) is an input the product
        // can never produce: it is rejected as VALIDATION_ERROR before the
        // follow-target check is consulted, and the test then proves nothing
        // about the thing it is named after. CI caught exactly that — the
        // prefixed form read perfectly well and was impossible.
        String artistAccountId = UUID.randomUUID().toString();
        String follower = UUID.randomUUID().toString();
        String stranger = UUID.randomUUID().toString();
        ConfirmingArtistAccountConfig.CONFIRMED.add(artistAccountId);

        // ── 1. A real fan follows the artist account, through the real API ──────
        ResponseEntity<String> followRes = rest.exchange(
                url("/api/community/follows"),
                HttpMethod.POST,
                new HttpEntity<>("{\"artistAccountId\":\"" + artistAccountId + "\"}",
                        authHeaders(jwt.signFanToken(follower))),
                String.class);
        assertThat(followRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // ── 2. A real ARTIST-role caller publishes an ARTIST_POST ───────────────
        // The author is NOT supplied by the request body — it is the authenticated
        // caller (ADR-MONO-059 excluded option B). So this is the only way a real
        // caller can produce the author id the feed joins on.
        ResponseEntity<String> publishRes = rest.exchange(
                url("/api/community/posts"),
                HttpMethod.POST,
                new HttpEntity<>("""
                        {"postType":"ARTIST_POST","visibility":"PUBLIC",\
                        "title":"Comeback","body":"See you at the showcase"}""",
                        authHeaders(jwt.signArtistToken(artistAccountId))),
                String.class);
        assertThat(publishRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode published = objectMapper.readTree(publishRes.getBody()).path("data");
        String postId = published.path("postId").asText();
        String authorAccountId = published.path("authorAccountId").asText();
        assertThat(postId).isNotBlank();

        // ── 3. The invariant the ticket was filed for, stated once, explicitly ──
        // Both ends of the feed join must be the same id space. Before AC-1b/AC-6
        // this held only by coincidence.
        Follow followRow = followJpaRepository
                .findByFanAccountIdAndArtistAccountIdAndTenantId(follower, artistAccountId, TENANT)
                .orElseThrow(() -> new AssertionError("follow row missing after a 201 follow"));
        assertThat(authorAccountId)
                .as("posts.author_account_id (from a real publish) must equal "
                        + "follows.artist_account_id (from a real follow) — this is the feed join")
                .isEqualTo(followRow.getArtistAccountId())
                .isEqualTo(artistAccountId);

        // ── 4. AC-2 — the follower's feed contains that post ────────────────────
        List<String> followerFeedIds = feedPostIds(follower);
        assertThat(followerFeedIds)
                .as("AC-2: an ARTIST_POST published by a real caller must reach the "
                        + "feed of a fan who follows that artist account")
                .contains(postId);

        JsonNode followerFeed = fetchFeed(follower);
        JsonNode item = null;
        for (JsonNode node : followerFeed.path("data").path("content")) {
            if (postId.equals(node.path("postId").asText())) {
                item = node;
            }
        }
        assertThat(item).isNotNull();
        assertThat(item.path("authorAccountId").asText()).isEqualTo(artistAccountId);
        assertThat(item.path("postType").asText()).isEqualTo("ARTIST_POST");

        // ── 5. AC-3 — the non-follower's feed does NOT ──────────────────────────
        // Same test, same post, same moment: without this, "joined" and "visible to
        // everyone" are indistinguishable.
        List<String> strangerFeedIds = feedPostIds(stranger);
        assertThat(strangerFeedIds)
                .as("AC-3: a fan who does not follow the artist must not see the post")
                .doesNotContain(postId);
        assertThat(fetchFeed(stranger).path("data").path("totalElements").asLong())
                .as("the non-follower follows nobody, so their feed is empty")
                .isZero();

        // The post really exists — the negative above is an absence from the feed,
        // not an absence from the database (a publish that silently wrote nothing
        // would otherwise satisfy step 5).
        assertThat(postJpaRepository.findByIdAndTenantId(postId, TENANT)).isPresent();
    }

    private JsonNode fetchFeed(String fanId) throws Exception {
        ResponseEntity<String> res = rest.exchange(
                url("/api/community/feed?page=0&size=20"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(jwt.signFanToken(fanId))),
                String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(res.getBody());
    }

    private List<String> feedPostIds(String fanId) throws Exception {
        List<String> ids = new ArrayList<>();
        for (JsonNode node : fetchFeed(fanId).path("data").path("content")) {
            ids.add(node.path("postId").asText());
        }
        return ids;
    }

    /**
     * Sanctioned test seam (see class Javadoc): confirms only the accounts a test
     * registers, denies everything else. NOT an always-allow — that shape is what
     * {@code ArtistAccountCheckerConfig} refuses to ship in production.
     */
    @TestConfiguration
    static class ConfirmingArtistAccountConfig {

        static final Set<String> CONFIRMED = ConcurrentHashMap.newKeySet();

        @Bean
        @Primary
        ArtistAccountChecker whitelistArtistAccountChecker() {
            return (accountId, tenantId) -> TENANT.equals(tenantId) && CONFIRMED.contains(accountId);
        }
    }
}
