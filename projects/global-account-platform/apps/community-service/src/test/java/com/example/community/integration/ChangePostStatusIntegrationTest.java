package com.example.community.integration;

import org.junit.jupiter.api.Tag;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.ActorType;
import com.example.community.infrastructure.persistence.PostJpaRepository;
import com.example.community.infrastructure.persistence.PostStatusHistoryJpaEntity;
import com.example.community.infrastructure.persistence.PostStatusHistoryJpaRepository;
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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Application integration test: ChangePostStatusUseCase end-to-end (TASK-BE-223).
 *
 * <p>Verifies PATCH /api/community/posts/{postId}/status: DRAFT→PUBLISHED history,
 * PUBLISHED→DELETED history, invalid transition 422, non-author 403, and not-found 404.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("ChangePostStatusUseCase 통합 테스트")
class ChangePostStatusIntegrationTest extends CommunityIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostJpaRepository postJpaRepository;

    @Autowired
    private PostStatusHistoryJpaRepository historyJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private void stubAccountProfile() {
        ACCOUNT_WM.stubFor(get(urlPathMatching("/internal/accounts/.*/profile"))
                .willReturn(okJson("{\"accountId\":\"x\",\"displayName\":\"Test\"}")));
    }

    /** Creates and persists a DRAFT post (no publish). */
    private String createDraftPost(String authorId) {
        Post post = Post.createDraft(authorId, PostType.ARTIST_POST, PostVisibility.PUBLIC,
                "Draft Title", "Draft body", null);
        transactionTemplate.executeWithoutResult(s -> postJpaRepository.save(post));
        return post.getId();
    }

    /** Creates and persists a PUBLISHED post. */
    private String createPublishedPost(String authorId) {
        Post post = Post.createDraft(authorId, PostType.ARTIST_POST, PostVisibility.PUBLIC,
                "Published Title", "Published body", null);
        post.publish(ActorType.AUTHOR);
        transactionTemplate.executeWithoutResult(s -> postJpaRepository.save(post));
        return post.getId();
    }

    @Test
    @DisplayName("DRAFT→PUBLISHED 전이 시 204 응답과 함께 post_status_history 에 기록 1건이 남는다")
    void changeStatus_draftToPublished_returns204AndRecordsHistory() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String postId = createDraftPost(artistId);

        mockMvc.perform(patch("/api/community/posts/" + postId + "/status")
                        .header("Authorization", bearerToken(artistId, List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isNoContent());

        List<PostStatusHistoryJpaEntity> histories =
                historyJpaRepository.findByPostIdOrderByOccurredAtAsc(postId);
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getFromStatus()).isEqualTo("DRAFT");
        assertThat(histories.get(0).getToStatus()).isEqualTo("PUBLISHED");
        assertThat(histories.get(0).getActorType()).isEqualTo("AUTHOR");
        assertThat(histories.get(0).getActorId()).isEqualTo(artistId);
    }

    @Test
    @DisplayName("PUBLISHED→DELETED 전이 시 204 응답과 함께 post_status_history 에 해당 기록이 추가된다")
    void changeStatus_publishedToDeleted_returns204AndRecordsHistory() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        // Create a DRAFT post and publish it via domain directly, then save
        Post post = Post.createDraft(artistId, PostType.ARTIST_POST, PostVisibility.PUBLIC,
                "Title", "body", null);
        post.publish(ActorType.AUTHOR);
        final String postId = post.getId();
        transactionTemplate.executeWithoutResult(s -> postJpaRepository.save(post));

        // Now PATCH to DELETED
        mockMvc.perform(patch("/api/community/posts/" + postId + "/status")
                        .header("Authorization", bearerToken(artistId, List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELETED\"}"))
                .andExpect(status().isNoContent());

        List<PostStatusHistoryJpaEntity> histories =
                historyJpaRepository.findByPostIdOrderByOccurredAtAsc(postId);
        // At least 1 entry for PUBLISHED→DELETED
        assertThat(histories).isNotEmpty();
        PostStatusHistoryJpaEntity last = histories.get(histories.size() - 1);
        assertThat(last.getFromStatus()).isEqualTo("PUBLISHED");
        assertThat(last.getToStatus()).isEqualTo("DELETED");
        assertThat(last.getActorType()).isEqualTo("AUTHOR");
        assertThat(last.getActorId()).isEqualTo(artistId);
    }

    @Test
    @DisplayName("DELETED→PUBLISHED 전이 시도 시 422 POST_STATUS_TRANSITION_INVALID 가 반환된다")
    void changeStatus_deletedToPublished_returns422() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String postId = createPublishedPost(artistId);

        // First, transition PUBLISHED→DELETED
        mockMvc.perform(patch("/api/community/posts/" + postId + "/status")
                        .header("Authorization", bearerToken(artistId, List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELETED\"}"))
                .andExpect(status().isNoContent());

        // Now attempt DELETED→PUBLISHED — must be 422
        mockMvc.perform(patch("/api/community/posts/" + postId + "/status")
                        .header("Authorization", bearerToken(artistId, List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POST_STATUS_TRANSITION_INVALID"));
    }

    @Test
    @DisplayName("비작성자가 상태 전이를 시도하면 403 PERMISSION_DENIED 가 반환된다")
    void changeStatus_nonAuthor_returns403() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String otherUserId = "other-" + UUID.randomUUID();
        String postId = createDraftPost(artistId);

        mockMvc.perform(patch("/api/community/posts/" + postId + "/status")
                        .header("Authorization", bearerToken(otherUserId, List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("존재하지 않는 postId 로 상태 전이를 시도하면 404 POST_NOT_FOUND 가 반환된다")
    void changeStatus_notFound_returns404() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String nonExistentPostId = UUID.randomUUID().toString();

        mockMvc.perform(patch("/api/community/posts/" + nonExistentPostId + "/status")
                        .header("Authorization", bearerToken(artistId, List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }
}
