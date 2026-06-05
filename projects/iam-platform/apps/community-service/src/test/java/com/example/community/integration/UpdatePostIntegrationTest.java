package com.example.community.integration;

import org.junit.jupiter.api.Tag;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.ActorType;
import com.example.community.infrastructure.persistence.PostJpaRepository;
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
 * Application integration test: UpdatePostUseCase end-to-end (TASK-BE-223).
 *
 * <p>Verifies PATCH /api/community/posts/{postId}: happy path (DB persistence),
 * non-author 403, and not-found 404.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("UpdatePostUseCase 통합 테스트")
class UpdatePostIntegrationTest extends CommunityIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostJpaRepository postJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private void stubAccountProfile() {
        ACCOUNT_WM.stubFor(get(urlPathMatching("/internal/accounts/.*/profile"))
                .willReturn(okJson("{\"accountId\":\"x\",\"displayName\":\"Test\"}")));
    }

    private String createPublishedPost(String authorId) {
        Post post = Post.createDraft(authorId, PostType.ARTIST_POST, PostVisibility.PUBLIC,
                "Original Title", "Original body", null);
        post.publish(ActorType.AUTHOR);
        transactionTemplate.executeWithoutResult(s -> postJpaRepository.save(post));
        return post.getId();
    }

    @Test
    @DisplayName("작성자가 PATCH 호출 시 200 응답과 함께 DB의 title/body/updatedAt 이 갱신된다")
    void updatePost_authorUpdatesContent_returns200AndPersists() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID().toString().substring(0, 20);
        String postId = createPublishedPost(artistId);

        String requestBody = """
                {"title":"Updated Title","body":"Updated body","mediaUrls":[]}
                """;

        mockMvc.perform(patch("/api/community/posts/" + postId)
                        .header("Authorization", bearerToken(artistId, List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(postId))
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.body").value("Updated body"))
                .andExpect(jsonPath("$.updatedAt").exists());

        Post reloaded = postJpaRepository.findById(postId).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Updated Title");
        assertThat(reloaded.getBody()).isEqualTo("Updated body");
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("비작성자가 PATCH 호출 시 403 PERMISSION_DENIED 가 반환된다")
    void updatePost_nonAuthor_returns403() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID().toString().substring(0, 20);
        String otherUserId = "other-" + UUID.randomUUID().toString().substring(0, 20);
        String postId = createPublishedPost(artistId);

        String requestBody = """
                {"title":"Hacked Title","body":"Hacked body","mediaUrls":[]}
                """;

        mockMvc.perform(patch("/api/community/posts/" + postId)
                        .header("Authorization", bearerToken(otherUserId, List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("존재하지 않는 postId 로 PATCH 호출 시 404 POST_NOT_FOUND 가 반환된다")
    void updatePost_notFound_returns404() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID().toString().substring(0, 20);
        String nonExistentPostId = UUID.randomUUID().toString();

        String requestBody = """
                {"title":"Any Title","body":"Any body","mediaUrls":[]}
                """;

        mockMvc.perform(patch("/api/community/posts/" + nonExistentPostId)
                        .header("Authorization", bearerToken(artistId, List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }
}
