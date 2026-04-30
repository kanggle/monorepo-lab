package com.example.community.integration;

import org.junit.jupiter.api.Tag;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.ActorType;
import com.example.community.domain.post.status.PostStatus;
import com.example.community.infrastructure.persistence.PostJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Application integration test: GetPostUseCase end-to-end (TASK-BE-225).
 *
 * <p>Verifies PUBLIC post retrieval, MEMBERS_ONLY denial for FREE fans,
 * membership-service 503 fail-closed semantics, DELETED post 404, and
 * non-existent post 404.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("GetPostUseCase 통합 테스트")
class GetPostIntegrationTest extends CommunityIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostJpaRepository postJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private void stubAccountProfile() {
        ACCOUNT_WM.stubFor(WireMock.get(urlPathMatching("/internal/accounts/.*/profile"))
                .willReturn(okJson("{\"accountId\":\"x\",\"displayName\":\"Test Artist\"}")));
    }

    private String createPublishedPost(String authorId, PostVisibility visibility) {
        Post post = Post.createDraft(authorId, PostType.ARTIST_POST, visibility,
                "Title", "body", null);
        post.publish(ActorType.AUTHOR);
        transactionTemplate.executeWithoutResult(s -> postJpaRepository.save(post));
        return post.getId();
    }

    private String createDeletedPost(String authorId) {
        Post post = Post.createDraft(authorId, PostType.ARTIST_POST, PostVisibility.PUBLIC,
                "Title", "body", null);
        post.publish(ActorType.AUTHOR);
        post.changeStatus(PostStatus.DELETED, ActorType.AUTHOR);
        transactionTemplate.executeWithoutResult(s -> postJpaRepository.save(post));
        return post.getId();
    }

    @Test
    @DisplayName("PUBLIC 포스트 조회 시 200 응답과 필수 필드가 반환된다")
    void getPost_publicPost_returns200WithFields() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String fanId = "fan-" + UUID.randomUUID();
        String postId = createPublishedPost(artistId, PostVisibility.PUBLIC);

        mockMvc.perform(get("/api/community/posts/" + postId)
                        .header("Authorization", bearerToken(fanId, List.of("FAN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(postId))
                .andExpect(jsonPath("$.type").value("ARTIST_POST"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.body").value("body"))
                .andExpect(jsonPath("$.commentCount").isNumber())
                .andExpect(jsonPath("$.reactionCount").isNumber());
    }

    @Test
    @DisplayName("MEMBERS_ONLY 포스트를 FREE 팬이 조회하면 403 MEMBERSHIP_REQUIRED 가 반환된다")
    void getPost_membersOnlyPost_freeFan_returns403() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String fanId = "fan-" + UUID.randomUUID();
        String postId = createPublishedPost(artistId, PostVisibility.MEMBERS_ONLY);

        MEMBERSHIP_WM.stubFor(WireMock.get(urlPathEqualTo("/internal/membership/access"))
                .willReturn(okJson("{\"accountId\":\"" + fanId
                        + "\",\"requiredPlanLevel\":\"FAN_CLUB\""
                        + ",\"allowed\":false,\"activePlanLevel\":\"FREE\"}")));

        mockMvc.perform(get("/api/community/posts/" + postId)
                        .header("Authorization", bearerToken(fanId, List.of("FAN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_REQUIRED"));
    }

    @Test
    @DisplayName("MEMBERS_ONLY 포스트에서 membership-service 가 503 일 때 fail-closed 로 403 가 반환된다")
    void getPost_membersOnlyPost_membershipService503_failsClosed_returns403() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String fanId = "fan-" + UUID.randomUUID();
        String postId = createPublishedPost(artistId, PostVisibility.MEMBERS_ONLY);

        MEMBERSHIP_WM.stubFor(WireMock.get(urlPathEqualTo("/internal/membership/access"))
                .willReturn(aResponse().withStatus(503)));

        mockMvc.perform(get("/api/community/posts/" + postId)
                        .header("Authorization", bearerToken(fanId, List.of("FAN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_REQUIRED"));
    }

    @Test
    @DisplayName("DELETED 포스트 조회 시 404 POST_NOT_FOUND 가 반환된다")
    void getPost_deletedPost_returns404() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String fanId = "fan-" + UUID.randomUUID();
        String postId = createDeletedPost(artistId);

        mockMvc.perform(get("/api/community/posts/" + postId)
                        .header("Authorization", bearerToken(fanId, List.of("FAN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("존재하지 않는 postId 조회 시 404 POST_NOT_FOUND 가 반환된다")
    void getPost_notFound_returns404() throws Exception {
        stubAccountProfile();
        String fanId = "fan-" + UUID.randomUUID();
        String randomPostId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/community/posts/" + randomPostId)
                        .header("Authorization", bearerToken(fanId, List.of("FAN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }
}
