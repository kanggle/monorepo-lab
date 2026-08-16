package com.example.fanplatform.community.integration;

import com.example.fanplatform.community.domain.follow.Follow;
import com.example.fanplatform.community.infrastructure.jpa.FollowJpaRepository;
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
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /api/community/follows/{artistAccountId}} — TASK-FAN-BE-049,
 * asserted through the HTTP API against real persistence.
 *
 * <h2>Why every case here is a pair, never a single cell</h2>
 *
 * The answer is one boolean, so <em>any</em> single assertion is satisfied by a
 * constant: an implementation that always returns {@code false} passes the
 * "not following" case, and one that always returns {@code true} passes the
 * "following" case. Each AC below therefore measures **two cells that must
 * differ**, and the two cells vary exactly one thing:
 *
 * <ul>
 *   <li>AC-3 varies <b>the target</b> — same caller, followed vs not-followed artist.</li>
 *   <li>AC-4 varies <b>the caller</b> — same artist, the fan who followed vs another fan.</li>
 * </ul>
 *
 * Without AC-4 an implementation that asks "does <em>anyone</em> in this tenant
 * follow the artist" passes AC-3 completely.
 *
 * <p>🔵 No artist-service stub is wired and none is needed: this endpoint
 * deliberately does not validate that the target exists (see
 * {@code community-api.md} § "This read does not validate that the artist
 * exists"). {@link #unknownArtistAccount_answersFalse_notAnError()} pins that
 * decision so a later "consistency" change to the write path's fail-closed
 * validation cannot silently drag the read along with it.
 */
class FollowStatusReadIntegrationTest extends CommunityServiceIntegrationBase {

    private static final String TENANT = "fan-platform";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    FollowJpaRepository followJpaRepository;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        truncateAll();
    }

    @AfterEach
    void cleanUp() {
        truncateAll();
    }

    /**
     * A bare UUID, 36 characters — the width every account id column in this
     * schema declares. {@code FollowArtistGateIntegrationTest} documents what a
     * readable-prefix id costs: it exceeds {@code VARCHAR(36)} and is rejected at
     * the controller boundary, so the assertions below would pass or fail for a
     * reason that has nothing to do with the behaviour under test.
     */
    private static String accountId() {
        return UUID.randomUUID().toString();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** Writes the row directly: this suite tests the READ, so the write path is not the subject. */
    private void seedFollow(String fanAccountId, String artistAccountId, String tenantId) {
        followJpaRepository.save(Follow.create(fanAccountId, artistAccountId, tenantId));
    }

    private ResponseEntity<String> askIsFollowing(String fanId, String artistAccountId) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.signFanToken(fanId));
        return rest.exchange(
                url("/api/community/follows/" + artistAccountId),
                HttpMethod.GET,
                new HttpEntity<>(h),
                String.class);
    }

    private boolean followingFlagOf(ResponseEntity<String> res) throws Exception {
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(res.getBody());
        JsonNode flag = body.path("data").path("following");
        assertThat(flag.isBoolean())
                .as("data.following must be a boolean, got: %s", body)
                .isTrue();
        return flag.asBoolean();
    }

    // ---- AC-3: vary the TARGET, hold the caller -----------------------------

    @Test
    @DisplayName("AC-3 대조군 — 같은 팬이 물으면 팔로우한 아티스트는 true, 안 한 아티스트는 false")
    void followedVsNotFollowed_answersDiffer() throws Exception {
        String fanId = accountId();
        String followedArtist = accountId();
        String unfollowedArtist = accountId();
        seedFollow(fanId, followedArtist, TENANT);

        boolean followed = followingFlagOf(askIsFollowing(fanId, followedArtist));
        boolean notFollowed = followingFlagOf(askIsFollowing(fanId, unfollowedArtist));

        assertThat(followed)
                .as("the artist this fan follows must read as followed")
                .isTrue();
        assertThat(notFollowed)
                .as("the artist this fan does not follow must read as not followed")
                .isFalse();
        assertThat(followed)
                .as("🔴 the two cells must DIFFER — equal answers mean a constant, "
                        + "and a constant passes either cell on its own")
                .isNotEqualTo(notFollowed);
    }

    @Test
    @DisplayName("🔴 팔로우하지 않은 대상 → 200 following=false (404 아님)")
    void notFollowing_is200False_not404() throws Exception {
        String fanId = accountId();

        ResponseEntity<String> res = askIsFollowing(fanId, accountId());

        // The sibling DELETE answers 404 NOT_FOLLOWING for this same condition.
        // On a read that status would also mean "no such artist", and the first
        // consumer (TASK-FAN-FE-017) renders a button off it — so the ambiguity
        // would land in the UI. community-api.md carries the full rationale.
        assertThat(res.getStatusCode())
                .as("a read must not reuse DELETE's 404 — it would collide with "
                        + "\"no such artist\"")
                .isEqualTo(HttpStatus.OK);
        assertThat(followingFlagOf(res)).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 아티스트 계정 → 200 following=false (검증하지 않는 것이 결정이다)")
    void unknownArtistAccount_answersFalse_notAnError() throws Exception {
        String fanId = accountId();
        String neverAnArtist = accountId();

        ResponseEntity<String> res = askIsFollowing(fanId, neverAnArtist);

        // Deliberate asymmetry with POST, which is fail-closed against
        // artist-service (ADR-004). Making the read fail-closed too would let an
        // artist-service outage blank the follow button on every artist page,
        // and would hand back the existence oracle ADR-004 avoids. If someone
        // later "aligns" the read with the write, this case fails first.
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(followingFlagOf(res))
                .as("following=false means \"you have no follow row\", not \"the artist exists\"")
                .isFalse();
    }

    // ---- AC-4: vary the CALLER, hold the target -----------------------------

    @Test
    @DisplayName("AC-4 격리 — 다른 팬의 팔로우는 내 답에 새지 않는다 (같은 아티스트, 팬만 다름)")
    void anotherFansFollowDoesNotLeakIntoMine() throws Exception {
        String artistAccountId = accountId();
        String fanWhoFollows = accountId();
        String fanWhoDoesNot = accountId();
        seedFollow(fanWhoFollows, artistAccountId, TENANT);

        boolean owner = followingFlagOf(askIsFollowing(fanWhoFollows, artistAccountId));
        boolean bystander = followingFlagOf(askIsFollowing(fanWhoDoesNot, artistAccountId));

        assertThat(owner).isTrue();
        assertThat(bystander)
                .as("🔴 an implementation that asks \"does anyone in this tenant follow "
                        + "the artist\" passes AC-3 entirely; this is the cell that catches it")
                .isFalse();
        assertThat(owner).isNotEqualTo(bystander);
    }

    @Test
    @DisplayName("테넌트 격리 — 다른 테넌트에 있는 같은 (팬, 아티스트) 쌍은 내 답에 새지 않는다")
    void followRowInAnotherTenantIsNotVisible() throws Exception {
        String fanId = accountId();
        String artistAccountId = accountId();
        // Same pair of ids, different tenant. The caller's token carries
        // fan-platform, so this row must be invisible to it.
        seedFollow(fanId, artistAccountId, "some-other-tenant");

        assertThat(followingFlagOf(askIsFollowing(fanId, artistAccountId)))
                .as("a follow row belonging to another tenant must not answer this caller")
                .isFalse();
        assertThat(followJpaRepository.count())
                .as("the foreign row must still exist — otherwise this case proves nothing")
                .isEqualTo(1);
    }

    // ---- AC-5 --------------------------------------------------------------

    @Test
    @DisplayName("AC-5 — 인증 없는 호출 → 401")
    void withoutBearer_returns401() {
        ResponseEntity<String> res = rest.exchange(
                url("/api/community/follows/" + accountId()),
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
