package com.example.community.integration;

import org.junit.jupiter.api.Tag;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.ActorType;
import com.example.community.domain.reaction.Reaction;
import com.example.community.infrastructure.persistence.PostJpaRepository;
import com.example.community.infrastructure.persistence.ReactionJpaRepository;
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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Application integration test: AddReactionUseCase end-to-end (TASK-BE-224).
 *
 * <p>Verifies upsert dedup (same emoji twice → 1 row), emoji replacement (different emoji → 1 row
 * with updated code), and the happy path (new reaction → 200 + outbox entry).
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AddReactionUseCase 통합 테스트")
class AddReactionIntegrationTest extends CommunityIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostJpaRepository postJpaRepository;

    @Autowired
    private ReactionJpaRepository reactionJpaRepository;

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
    @DisplayName("팬이 PUBLIC 포스트에 처음 반응하면 200 응답과 함께 DB 행·outbox 가 생성된다")
    void addReaction_newReaction_returns200AndPersists() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String fanId = "fan-" + UUID.randomUUID();
        String postId = createPublishedPost(artistId, PostVisibility.PUBLIC);

        mockMvc.perform(post("/api/community/posts/" + postId + "/reactions")
                        .header("Authorization", bearerToken(fanId, List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emojiCode\":\"HEART\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(postId))
                .andExpect(jsonPath("$.emojiCode").value("HEART"))
                .andExpect(jsonPath("$.totalReactions").value(1));

        // DB: exactly 1 reaction row for this fan+post
        List<Reaction> reactions = reactionJpaRepository.findAll().stream()
                .filter(r -> postId.equals(r.getPostId()))
                .filter(r -> fanId.equals(r.getAccountId()))
                .toList();
        assertThat(reactions).hasSize(1);
        assertThat(reactions.get(0).getEmojiCode()).isEqualTo("HEART");

        // Outbox: 1 entry with event_type community.reaction.added
        List<OutboxJpaEntity> outboxRows = outboxJpaRepository.findAll().stream()
                .filter(e -> postId.equals(e.getAggregateId()))
                .filter(e -> "community.reaction.added".equals(e.getEventType()))
                .toList();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).getAggregateType()).isEqualTo("community");
    }

    @Test
    @DisplayName("같은 emojiCode 로 두 번 반응해도 DB 행은 1개 유지된다 (upsert dedup)")
    void addReaction_sameEmojiTwice_upsertsToSingleRow() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String fanId = "fan-" + UUID.randomUUID();
        String postId = createPublishedPost(artistId, PostVisibility.PUBLIC);

        String requestBody = "{\"emojiCode\":\"FIRE\"}";

        // First call
        mockMvc.perform(post("/api/community/posts/" + postId + "/reactions")
                        .header("Authorization", bearerToken(fanId, List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // Second call with same emoji
        mockMvc.perform(post("/api/community/posts/" + postId + "/reactions")
                        .header("Authorization", bearerToken(fanId, List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // DB: still only 1 reaction row for this fan+post
        long reactionCount = reactionJpaRepository.findAll().stream()
                .filter(r -> postId.equals(r.getPostId()))
                .filter(r -> fanId.equals(r.getAccountId()))
                .count();
        assertThat(reactionCount).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 emojiCode 로 반응 교체 시 DB 행은 1개이고 emojiCode 가 변경된다")
    void addReaction_differentEmoji_replacesExisting() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String fanId = "fan-" + UUID.randomUUID();
        String postId = createPublishedPost(artistId, PostVisibility.PUBLIC);

        // First reaction: HEART
        mockMvc.perform(post("/api/community/posts/" + postId + "/reactions")
                        .header("Authorization", bearerToken(fanId, List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emojiCode\":\"HEART\"}"))
                .andExpect(status().isOk());

        // Second reaction: FIRE (different emoji)
        mockMvc.perform(post("/api/community/posts/" + postId + "/reactions")
                        .header("Authorization", bearerToken(fanId, List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emojiCode\":\"FIRE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emojiCode").value("FIRE"));

        // DB: still only 1 reaction row, and emoji is now FIRE
        List<Reaction> reactions = reactionJpaRepository.findAll().stream()
                .filter(r -> postId.equals(r.getPostId()))
                .filter(r -> fanId.equals(r.getAccountId()))
                .toList();
        assertThat(reactions).hasSize(1);
        assertThat(reactions.get(0).getEmojiCode()).isEqualTo("FIRE");
    }
}
