package com.example.community.integration;

import com.example.community.domain.feed.FeedSubscription;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.ActorType;
import com.example.community.domain.post.status.PostStatus;
import com.example.community.infrastructure.persistence.FeedSubscriptionJpaRepository;
import com.example.community.infrastructure.persistence.PostJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Application integration test: GetFeedUseCase end-to-end (TASK-BE-224).
 *
 * <p>Verifies that the feed includes only PUBLISHED posts from followed artists and that
 * DELETED and HIDDEN posts are excluded regardless of the follow relationship.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("GetFeedUseCase 통합 테스트")
class GetFeedIntegrationTest extends CommunityIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostJpaRepository postJpaRepository;

    @Autowired
    private FeedSubscriptionJpaRepository feedSubscriptionJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private void stubAccountProfile() {
        ACCOUNT_WM.stubFor(WireMock.get(urlPathMatching("/internal/accounts/.*/profile"))
                .willReturn(okJson("{\"accountId\":\"x\",\"displayName\":\"Test Artist\"}")));
    }

    /** Creates a follow relationship: fan subscribes to artist. */
    private void follow(String fanId, String artistId) {
        FeedSubscription subscription = FeedSubscription.create(fanId, artistId, Instant.now());
        transactionTemplate.executeWithoutResult(s -> feedSubscriptionJpaRepository.save(subscription));
    }

    /** Creates and persists a PUBLISHED post for the given author. Returns the postId. */
    private String createPublishedPost(String authorId) {
        Post post = Post.createDraft(authorId, PostType.ARTIST_POST, PostVisibility.PUBLIC,
                "Title", "Body content", null);
        post.publish(ActorType.AUTHOR);
        transactionTemplate.executeWithoutResult(s -> postJpaRepository.save(post));
        return post.getId();
    }

    /** Creates a PUBLISHED post then applies a status change to the given target status. Returns the postId. */
    private String createPostWithStatus(String authorId, PostStatus targetStatus) {
        Post post = Post.createDraft(authorId, PostType.ARTIST_POST, PostVisibility.PUBLIC,
                "Title", "Body content", null);
        post.publish(ActorType.AUTHOR);
        post.changeStatus(targetStatus, ActorType.AUTHOR);
        transactionTemplate.executeWithoutResult(s -> postJpaRepository.save(post));
        return post.getId();
    }

    @Test
    @DisplayName("팔로우한 아티스트의 PUBLISHED 포스트는 피드에 포함된다")
    void getFeed_includesPublishedPostsFromFollowedArtist() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String fanId = "fan-" + UUID.randomUUID();

        follow(fanId, artistId);
        String postId = createPublishedPost(artistId);

        mockMvc.perform(get("/api/community/feed")
                        .header("Authorization", bearerToken(fanId, List.of("FAN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.content[?(@.postId == '" + postId + "')]").exists());
    }

    @Test
    @DisplayName("팔로우한 아티스트의 DELETED 포스트는 피드에 미노출된다")
    void getFeed_excludesDeletedPosts() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String fanId = "fan-" + UUID.randomUUID();

        follow(fanId, artistId);
        String publishedPostId = createPublishedPost(artistId);
        createPostWithStatus(artistId, PostStatus.DELETED);

        mockMvc.perform(get("/api/community/feed")
                        .header("Authorization", bearerToken(fanId, List.of("FAN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[?(@.postId == '" + publishedPostId + "')]").exists());
    }

    @Test
    @DisplayName("팔로우한 아티스트의 HIDDEN 포스트는 피드에 미노출된다")
    void getFeed_excludesHiddenPosts() throws Exception {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String fanId = "fan-" + UUID.randomUUID();

        follow(fanId, artistId);
        String publishedPostId = createPublishedPost(artistId);
        // HIDDEN transition: PUBLISHED -> HIDDEN by AUTHOR
        Post hiddenPost = Post.createDraft(artistId, PostType.ARTIST_POST, PostVisibility.PUBLIC,
                "Hidden Title", "Hidden body", null);
        hiddenPost.publish(ActorType.AUTHOR);
        hiddenPost.changeStatus(PostStatus.HIDDEN, ActorType.AUTHOR);
        transactionTemplate.executeWithoutResult(s -> postJpaRepository.save(hiddenPost));

        mockMvc.perform(get("/api/community/feed")
                        .header("Authorization", bearerToken(fanId, List.of("FAN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[?(@.postId == '" + publishedPostId + "')]").exists());
    }

    @Test
    @DisplayName("팔로우하지 않은 아티스트의 포스트는 피드에 미포함된다")
    void getFeed_excludesPostsFromNonFollowedArtist() throws Exception {
        stubAccountProfile();
        String artistA = "artistA-" + UUID.randomUUID();
        String artistB = "artistB-" + UUID.randomUUID();
        String fanId = "fan-" + UUID.randomUUID();

        // Fan follows only artistA
        follow(fanId, artistA);
        String artistAPostId = createPublishedPost(artistA);

        // artistB has a PUBLISHED post but fan does not follow artistB
        String artistBPostId = createPublishedPost(artistB);

        mockMvc.perform(get("/api/community/feed")
                        .header("Authorization", bearerToken(fanId, List.of("FAN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.postId == '" + artistAPostId + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.postId == '" + artistBPostId + "')]").doesNotExist());
    }
}
