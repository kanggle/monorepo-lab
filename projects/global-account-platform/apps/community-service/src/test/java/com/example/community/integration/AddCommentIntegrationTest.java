package com.example.community.integration;

import org.junit.jupiter.api.Tag;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.ActorType;
import com.example.community.infrastructure.persistence.CommentJpaRepository;
import com.example.community.infrastructure.persistence.PostJpaRepository;
import com.example.messaging.outbox.OutboxJpaEntity;
import com.example.messaging.outbox.OutboxJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Application integration test: AddCommentUseCase end-to-end (TASK-BE-149).
 *
 * <p>Verifies happy path (PUBLIC post + DB row + outbox), MEMBERS_ONLY denial,
 * and membership-service 503 fail-closed semantics.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AddCommentUseCase 통합 테스트")
class AddCommentIntegrationTest extends CommunityIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostJpaRepository postJpaRepository;

    @Autowired
    private CommentJpaRepository commentJpaRepository;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private void stubAccountProfile() {
        ACCOUNT_WM.stubFor(get(urlPathMatching("/internal/accounts/.*/profile"))
                .willReturn(okJson("{\"accountId\":\"x\",\"displayName\":\"Test\"}")));
    }

    private String createPublishedPost(String authorId, PostVisibility visibility) {
        Post post = Post.createDraft(authorId, PostType.ARTIST_POST, visibility,
                "Title", "body", null);
        post.publish(ActorType.AUTHOR);
        transactionTemplate.executeWithoutResult(s -> postJpaRepository.save(post));
        return post.getId();
    }

    @Test
    @DisplayName("PUBLIC 포스트에 댓글이 성공적으로 작성되면 201 응답과 함께 DB·outbox 가 채워진다")
    void addComment_toPublicPost_returns201_andPersistsOutbox() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID().toString().substring(0, 20);
        String fanId = "fan-" + UUID.randomUUID().toString().substring(0, 20);
        String postId = createPublishedPost(artistId, PostVisibility.PUBLIC);

        mockMvc.perform(post("/api/community/posts/" + postId + "/comments")
                        .header("Authorization", bearerToken(fanId, List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Nice!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commentId").exists())
                .andExpect(jsonPath("$.postId").value(postId));

        // Comment row exists
        long commentCount = commentJpaRepository.findAll().stream()
                .filter(c -> postId.equals(c.getPostId()))
                .filter(c -> fanId.equals(c.getAuthorAccountId()))
                .count();
        assertThat(commentCount).isEqualTo(1);

        // Outbox row for community.comment.created
        List<OutboxJpaEntity> outboxRows = outboxJpaRepository.findAll().stream()
                .filter(e -> postId.equals(e.getAggregateId()))
                .filter(e -> "community.comment.created".equals(e.getEventType()))
                .toList();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).getAggregateType()).isEqualTo("community");
    }

    @Test
    @DisplayName("MEMBERS_ONLY 포스트에 FREE 팬이 댓글을 시도하면 403 MEMBERSHIP_REQUIRED 가 반환된다")
    void addComment_toMembersOnlyPost_byFreeFan_returns403() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID().toString().substring(0, 20);
        String fanId = "fan-" + UUID.randomUUID().toString().substring(0, 20);
        String postId = createPublishedPost(artistId, PostVisibility.MEMBERS_ONLY);

        MEMBERSHIP_WM.stubFor(get(urlPathEqualTo("/internal/membership/access"))
                .willReturn(okJson("{\"accountId\":\"" + fanId
                        + "\",\"requiredPlanLevel\":\"FAN_CLUB\""
                        + ",\"allowed\":false,\"activePlanLevel\":\"FREE\"}")));

        mockMvc.perform(post("/api/community/posts/" + postId + "/comments")
                        .header("Authorization", bearerToken(fanId, List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_REQUIRED"));
    }

    @Test
    @DisplayName("MEMBERS_ONLY 포스트에서 membership-service 가 503 일 때 fail-closed 로 403 가 반환된다")
    void addComment_toMembersOnlyPost_membershipServiceDown_failsClosed_returns403() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID().toString().substring(0, 20);
        String fanId = "fan-" + UUID.randomUUID().toString().substring(0, 20);
        String postId = createPublishedPost(artistId, PostVisibility.MEMBERS_ONLY);

        MEMBERSHIP_WM.stubFor(get(urlPathEqualTo("/internal/membership/access"))
                .willReturn(aResponse().withStatus(503)));

        mockMvc.perform(post("/api/community/posts/" + postId + "/comments")
                        .header("Authorization", bearerToken(fanId, List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_REQUIRED"));
    }
}
