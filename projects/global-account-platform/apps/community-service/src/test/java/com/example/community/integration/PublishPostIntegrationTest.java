package com.example.community.integration;

import org.junit.jupiter.api.Tag;
import com.example.community.infrastructure.persistence.PostJpaRepository;
import com.example.community.infrastructure.persistence.PostStatusHistoryJpaEntity;
import com.example.community.infrastructure.persistence.PostStatusHistoryJpaRepository;
import com.example.messaging.outbox.OutboxJpaEntity;
import com.example.messaging.outbox.OutboxJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
 * Application integration test: PublishPostUseCase end-to-end (TASK-BE-149).
 *
 * <p>Exercises HTTP -> controller -> use-case -> JPA persistence -> outbox writer.
 * Verifies post row, status history row, and outbox row all land in MySQL.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("PublishPostUseCase 통합 테스트")
class PublishPostIntegrationTest extends CommunityIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostJpaRepository postJpaRepository;

    @Autowired
    private PostStatusHistoryJpaRepository historyJpaRepository;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    private void stubAccountProfile(String accountId, String displayName) {
        ACCOUNT_WM.stubFor(get(urlPathMatching("/internal/accounts/.*/profile"))
                .willReturn(okJson("{\"accountId\":\"" + accountId
                        + "\",\"displayName\":\"" + displayName + "\"}")));
    }

    @Test
    @DisplayName("아티스트가 ARTIST_POST 발행에 성공하면 201 응답과 함께 DB·status_history·outbox 가 채워진다")
    void artistPublishesPost_returns201_andPersistsToDb() throws Exception {
        stubAccountProfile("artist", "Test Artist");
        String artistId = "artist-" + UUID.randomUUID();

        String body = """
                {"type":"ARTIST_POST","visibility":"PUBLIC","title":"Test Post","body":"Hello world","mediaUrls":[]}
                """;

        String response = mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", bearerToken(artistId, List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").exists())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.type").value("ARTIST_POST"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("postId");

        // Find the post by author (use-case generates the postId)
        var posts = postJpaRepository.findAll().stream()
                .filter(p -> artistId.equals(p.getAuthorAccountId()))
                .toList();
        assertThat(posts).hasSize(1);
        String postId = posts.get(0).getId();

        // Status history: 1 entry DRAFT -> PUBLISHED
        List<PostStatusHistoryJpaEntity> histories =
                historyJpaRepository.findByPostIdOrderByOccurredAtAsc(postId);
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getFromStatus()).isEqualTo("DRAFT");
        assertThat(histories.get(0).getToStatus()).isEqualTo("PUBLISHED");
        assertThat(histories.get(0).getActorType()).isEqualTo("AUTHOR");
        assertThat(histories.get(0).getActorId()).isEqualTo(artistId);

        // Outbox: 1 unpublished entry with event_type "community.post.published"
        List<OutboxJpaEntity> outboxRows = outboxJpaRepository.findAll().stream()
                .filter(e -> postId.equals(e.getAggregateId()))
                .toList();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).getEventType()).isEqualTo("community.post.published");
        assertThat(outboxRows.get(0).getAggregateType()).isEqualTo("community");
    }

    @Test
    @DisplayName("팬이 ARTIST_POST 를 발행하려고 하면 403 PERMISSION_DENIED 가 반환된다")
    void fanCannotPublishArtistPost_returns403() throws Exception {
        String fanId = "fan-" + UUID.randomUUID();

        String body = """
                {"type":"ARTIST_POST","visibility":"PUBLIC","title":"Test Post","body":"Hello world","mediaUrls":[]}
                """;

        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", bearerToken(fanId, List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }
}
