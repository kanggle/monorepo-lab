package com.example.fanplatform.community.integration;

import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.domain.reaction.Reaction;
import com.example.fanplatform.community.domain.reaction.ReactionType;
import com.example.fanplatform.community.infrastructure.jpa.PostJpaRepository;
import com.example.fanplatform.community.infrastructure.jpa.ReactionJpaRepository;
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
 * {@code myReaction} on {@code GET /api/community/posts/{id}} — TASK-FAN-BE-051,
 * asserted through the HTTP API against real persistence.
 *
 * <h2>Why AC-3 is a triple, never a single cell</h2>
 *
 * A hardcoded {@code null} passes the "no reaction" cell alone, so AC-3 requires
 * the three cells below to differ pairwise: no reaction / {@code LIKE} /
 * {@code LIKE}→{@code LOVE} (the third cell additionally proves the upsert
 * overwrite is visible on read, not just on the write response).
 *
 * <h2>Why AC-4 is a separate axis from AC-3</h2>
 *
 * AC-3 varies the reaction state for ONE fan. AC-4 varies the CALLER for one
 * fixed reaction row: without it, an implementation that answers "the post's
 * reaction, picked arbitrarily" would still pass every AC-3 cell.
 */
class ReactionMyStatusReadIntegrationTest extends CommunityServiceIntegrationBase {

    private static final String TENANT = "fan-platform";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    PostJpaRepository postJpaRepository;

    @Autowired
    ReactionJpaRepository reactionJpaRepository;

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

    private static String accountId() {
        return UUID.randomUUID().toString();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** Seeds a PUBLIC, PUBLISHED post directly — this suite tests the READ, not publish. */
    private String seedPublishedPost(String authorAccountId) {
        String postId = UUID.randomUUID().toString();
        Post post = Post.createDraft(postId, TENANT, authorAccountId,
                PostType.ARTIST_POST, PostVisibility.PUBLIC, "t", "b", null);
        post.publish(ActorType.AUTHOR);
        postJpaRepository.save(post);
        return postId;
    }

    /** Writes the reaction row directly, bypassing PUT — this suite tests the READ. */
    private void seedReaction(String postId, String reactorAccountId, ReactionType type) {
        reactionJpaRepository.save(Reaction.create(postId, reactorAccountId, TENANT, type));
    }

    private ResponseEntity<String> getPost(String fanId, String postId) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.signFanToken(fanId));
        return rest.exchange(
                url("/api/community/posts/" + postId),
                HttpMethod.GET,
                new HttpEntity<>(h),
                String.class);
    }

    private JsonNode dataOf(ResponseEntity<String> res) throws Exception {
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(res.getBody()).path("data");
    }

    // ---- AC-3: vary the REACTION STATE, hold the caller ---------------------

    @Test
    @DisplayName("AC-3 cell 1 — 반응 안 함 → myReaction = null")
    void noReaction_myReactionIsNull() throws Exception {
        String fanId = accountId();
        String postId = seedPublishedPost(accountId());

        JsonNode data = dataOf(getPost(fanId, postId));

        assertThat(data.path("myReaction").isNull())
                .as("no reaction row for this (post, fan) must read as null, got: %s", data)
                .isTrue();
    }

    @Test
    @DisplayName("AC-3 cell 2 — LIKE 반응 중 → myReaction = \"LIKE\"")
    void likeReaction_myReactionIsLike() throws Exception {
        String fanId = accountId();
        String postId = seedPublishedPost(accountId());
        seedReaction(postId, fanId, ReactionType.LIKE);

        JsonNode data = dataOf(getPost(fanId, postId));

        assertThat(data.path("myReaction").asText()).isEqualTo("LIKE");
    }

    @Test
    @DisplayName("AC-3 cell 3 — LIKE → LOVE 로 바꾼 뒤 → myReaction = \"LOVE\" (이전 값이 남지 않음)")
    void changedReaction_myReactionIsTheNewType() throws Exception {
        String fanId = accountId();
        String postId = seedPublishedPost(accountId());
        seedReaction(postId, fanId, ReactionType.LIKE);

        // Flip LIKE -> LOVE through the real write path (PUT), not a second seed —
        // this exercises the actual upsert-in-place the GET must then observe.
        String putBody = """
                {"reactionType":"LOVE"}
                """;
        HttpHeaders h = new HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        h.setBearerAuth(jwt.signFanToken(fanId));
        ResponseEntity<String> putRes = rest.exchange(
                url("/api/community/posts/" + postId + "/reactions"),
                HttpMethod.PUT,
                new HttpEntity<>(putBody, h),
                String.class);
        assertThat(putRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode data = dataOf(getPost(fanId, postId));

        assertThat(data.path("myReaction").asText())
                .as("the GET must reflect the CURRENT type, not the one it was created with")
                .isEqualTo("LOVE");
    }

    @Test
    @DisplayName("🔴 AC-3 대조군 — 세 칸이 서로 달라야 한다(상수 구현이면 어느 한 칸에서 실패)")
    void theThreeCellsDiffer() throws Exception {
        String fanId = accountId();
        String noneTargetPost = seedPublishedPost(accountId());
        String likeTargetPost = seedPublishedPost(accountId());
        seedReaction(likeTargetPost, fanId, ReactionType.LIKE);

        String none = dataOf(getPost(fanId, noneTargetPost)).path("myReaction").asText(null);
        String like = dataOf(getPost(fanId, likeTargetPost)).path("myReaction").asText(null);

        assertThat(none).isNull();
        assertThat(like).isEqualTo("LIKE");
        assertThat(none)
                .as("🔴 the two observed cells must DIFFER — equal answers mean a constant")
                .isNotEqualTo(like);
    }

    // ---- AC-4: vary the CALLER, hold the reaction row -----------------------

    @Test
    @DisplayName("AC-4 격리 — 다른 팬의 반응이 내 답에 새지 않는다 (같은 글, 팬만 다름)")
    void anotherFansReactionDoesNotLeakIntoMine() throws Exception {
        String postId = seedPublishedPost(accountId());
        String fanWhoReacted = accountId();
        String bystanderFan = accountId();
        seedReaction(postId, fanWhoReacted, ReactionType.FIRE);

        JsonNode ownerView = dataOf(getPost(fanWhoReacted, postId));
        JsonNode bystanderView = dataOf(getPost(bystanderFan, postId));

        assertThat(ownerView.path("myReaction").asText()).isEqualTo("FIRE");
        assertThat(bystanderView.path("myReaction").isNull())
                .as("🔴 an implementation that answers \"the post's reaction, picked "
                        + "arbitrarily\" passes the owner's cell but must fail here")
                .isTrue();
        // The aggregate is unaffected by whose reaction is asked about — the
        // reaction still counts, it just does not attribute to the bystander.
        assertThat(ownerView.path("reactionCount").asLong())
                .isEqualTo(bystanderView.path("reactionCount").asLong())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("테넌트 격리 — 다른 테넌트의 같은 (post_id, reactor) 행은 내 답에 새지 않는다")
    void reactionRowInAnotherTenantIsNotVisible() throws Exception {
        String fanId = accountId();
        String postId = seedPublishedPost(accountId());
        // Same ids, different tenant — the caller's token carries fan-platform.
        reactionJpaRepository.save(Reaction.create(postId, fanId, "some-other-tenant", ReactionType.SAD));

        JsonNode data = dataOf(getPost(fanId, postId));

        assertThat(data.path("myReaction").isNull())
                .as("a reaction row belonging to another tenant must not answer this caller")
                .isTrue();
        assertThat(reactionJpaRepository.count())
                .as("the foreign row must still exist — otherwise this case proves nothing")
                .isEqualTo(1);
    }

    // ---- AC-5 ----------------------------------------------------------------

    @Test
    @DisplayName("AC-5 — 인증 없는 호출 → 401")
    void withoutBearer_returns401() {
        String postId = seedPublishedPost(accountId());

        ResponseEntity<String> res = rest.exchange(
                url("/api/community/posts/" + postId),
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
