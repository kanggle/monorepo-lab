package com.example.fanplatform.community.integration;

import com.example.fanplatform.community.application.PostMediaRefSerializer;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.domain.post.status.PostStatus;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /api/community/posts/mine} against a real Postgres (TASK-FAN-FE-016).
 *
 * <p>This covers what {@code GetMyPostsUseCaseTest} structurally cannot: the JPQL itself.
 * That unit test mocks {@code PostRepository}, so the author scoping, the DELETED exclusion
 * and the ordering are assertions about a mock — the query behind them had no test at all
 * until this one.
 */
class MyPostsQueryIntegrationTest extends CommunityServiceIntegrationBase {

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

    private String fanId;
    private String otherFanId;

    @BeforeEach
    void seed() {
        postJpaRepository.deleteAll();
        fanId = "fan-" + UUID.randomUUID().toString().substring(0, 8);
        otherFanId = "other-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void cleanUp() {
        postJpaRepository.deleteAll();
    }

    private Post persist(String author, String title, PostVisibility visibility, boolean publish) {
        Post p = Post.createDraft(
                UUID.randomUUID().toString(),
                "fan-platform",
                author,
                PostType.FAN_POST,
                visibility,
                title,
                "body of " + title,
                mediaRefSerializer.serialize(null));
        if (publish) {
            p.publish(ActorType.AUTHOR);
        }
        return postJpaRepository.saveAndFlush(p);
    }

    private JsonNode fetchMine(String bearer, String query) throws Exception {
        ResponseEntity<String> res = rest.exchange(
                "http://localhost:" + port + "/api/community/posts/mine" + query,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(bearer)),
                String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(res.getBody());
    }

    private HttpHeaders authHeaders(String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(bearer);
        return h;
    }

    private static List<String> titles(JsonNode page) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : page.path("data").path("content")) {
            out.add(n.path("title").asText());
        }
        return out;
    }

    @Test
    @DisplayName("자기 글만 돌아온다 — 다른 팬의 글은 절대 섞이지 않는다")
    void returnsOnlyTheCallersPosts() throws Exception {
        persist(fanId, "mine-1", PostVisibility.PUBLIC, true);
        persist(otherFanId, "not-mine", PostVisibility.PUBLIC, true);

        JsonNode page = fetchMine(jwt.signFanToken(fanId), "");

        // 양성만 보면 "목록이 렌더됐다" 와 구별되지 않는다. 남의 글의 **부재**가 스코프의 증거다.
        assertThat(titles(page)).containsExactly("mine-1");
        assertThat(page.path("data").path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("🔴 DELETED 는 빠지고 DRAFT·HIDDEN 은 남는다 — 저자에게 임시저장이 사라지면 안 된다")
    void excludesDeletedButKeepsDraftAndHidden() throws Exception {
        persist(fanId, "published", PostVisibility.PUBLIC, true);
        persist(fanId, "draft", PostVisibility.PUBLIC, false);
        Post hidden = persist(fanId, "hidden", PostVisibility.PUBLIC, true);
        hidden.changeStatus(PostStatus.HIDDEN, ActorType.AUTHOR);
        postJpaRepository.saveAndFlush(hidden);
        Post deleted = persist(fanId, "deleted", PostVisibility.PUBLIC, true);
        deleted.changeStatus(PostStatus.DELETED, ActorType.AUTHOR);
        postJpaRepository.saveAndFlush(deleted);

        JsonNode page = fetchMine(jwt.signFanToken(fanId), "");

        assertThat(titles(page)).containsExactlyInAnyOrder("published", "draft", "hidden");
        assertThat(titles(page)).doesNotContain("deleted");
    }

    @Test
    @DisplayName("🔴 자기 PREMIUM 글도 제목·본문이 그대로 온다 (멤버십과 무관)")
    void ownGatedPostIsNotRedacted() throws Exception {
        persist(fanId, "premium-mine", PostVisibility.PREMIUM, true);

        JsonNode item = fetchMine(jwt.signFanToken(fanId), "").path("data").path("content").get(0);

        // 이 슬라이스에서는 membership-service 가 없어 체커가 fail-closed 로 거절한다.
        // 그런데도 제목이 온다는 것이 "저자 경로에는 게이트가 없다" 의 실측이다.
        assertThat(item.path("title").asText()).isEqualTo("premium-mine");
        assertThat(item.path("body").asText()).isEqualTo("body of premium-mine");
        assertThat(item.path("visibility").asText()).isEqualTo("PREMIUM");
    }

    @Test
    @DisplayName("페이지네이션 — page=1&size=2 는 다음 2건이고 page=0 과 겹치지 않는다")
    void paginates() throws Exception {
        for (int i = 0; i < 5; i++) {
            persist(fanId, "p" + i, PostVisibility.PUBLIC, true);
        }

        JsonNode page0 = fetchMine(jwt.signFanToken(fanId), "?page=0&size=2");
        JsonNode page1 = fetchMine(jwt.signFanToken(fanId), "?page=1&size=2");

        assertThat(titles(page0)).hasSize(2);
        assertThat(titles(page1)).hasSize(2);
        assertThat(titles(page0)).doesNotContainAnyElementsOf(titles(page1));
        assertThat(page0.path("data").path("totalElements").asLong()).isEqualTo(5);
        assertThat(page0.path("data").path("hasNext").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("글이 없으면 빈 목록과 totalElements=0 이다 (404 가 아니다)")
    void emptyIsAnEmptyList() throws Exception {
        JsonNode page = fetchMine(jwt.signFanToken(fanId), "");

        assertThat(titles(page)).isEmpty();
        assertThat(page.path("data").path("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("인증 없이는 401")
    void withoutAuth_401() {
        ResponseEntity<String> res = rest.exchange(
                "http://localhost:" + port + "/api/community/posts/mine",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
