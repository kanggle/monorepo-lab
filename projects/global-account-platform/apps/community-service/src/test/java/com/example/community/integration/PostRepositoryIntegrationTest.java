package com.example.community.integration;

import com.example.community.domain.feed.FeedSubscription;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.ActorType;
import com.example.community.infrastructure.persistence.FeedSubscriptionJpaRepository;
import com.example.community.infrastructure.persistence.PostJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice integration tests for {@link PostRepository#findFeedForFan} (TASK-BE-149).
 *
 * <p>Uses real MySQL via {@link com.example.testsupport.integration.AbstractIntegrationTest}.
 * Validates: PUBLISHED 필터, 팔로우 범위, soft-delete 제외, 페이지네이션.
 */
@SpringBootTest
@DisplayName("PostRepository.findFeedForFan 통합 테스트")
class PostRepositoryIntegrationTest extends CommunityIntegrationTestBase {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostJpaRepository postJpaRepository;

    @Autowired
    private FeedSubscriptionJpaRepository feedSubscriptionJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private void stubAccountProfile() {
        ACCOUNT_WM.stubFor(get(urlPathMatching("/internal/accounts/.*/profile"))
                .willReturn(okJson("{\"accountId\":\"x\",\"displayName\":\"Test\"}")));
    }

    private Post createPublishedPost(String authorAccountId, String title) {
        Post post = Post.createDraft(authorAccountId, PostType.ARTIST_POST,
                PostVisibility.PUBLIC, title, "body", null);
        post.publish(ActorType.AUTHOR);
        return post;
    }

    private Post createDraftPost(String authorAccountId, String title) {
        return Post.createDraft(authorAccountId, PostType.ARTIST_POST,
                PostVisibility.PUBLIC, title, "body", null);
    }

    @Test
    @DisplayName("팔로잉 아티스트의 PUBLISHED 포스트만 피드에 노출된다")
    void findFeedForFan_returnsOnlyPublishedPostsFromFollowedArtists() {
        stubAccountProfile();
        String fanId = "fan-" + UUID.randomUUID();
        String artistId = "artist-" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(s -> {
            feedSubscriptionJpaRepository.save(FeedSubscription.create(fanId, artistId, Instant.now()));
            postJpaRepository.save(createPublishedPost(artistId, "Published-1"));
            postJpaRepository.save(createDraftPost(artistId, "Draft-1"));
        });

        Page<Post> page = postRepository.findFeedForFan(fanId, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Published-1");
    }

    @Test
    @DisplayName("팔로우하지 않은 아티스트의 포스트는 피드에 노출되지 않는다")
    void findFeedForFan_excludesPostsFromUnfollowedArtists() {
        stubAccountProfile();
        String fanId = "fan-" + UUID.randomUUID();
        String artistA = "artist-a-" + UUID.randomUUID();
        String artistB = "artist-b-" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(s -> {
            feedSubscriptionJpaRepository.save(FeedSubscription.create(fanId, artistA, Instant.now()));
            postJpaRepository.save(createPublishedPost(artistA, "ArtistA-Post"));
            postJpaRepository.save(createPublishedPost(artistB, "ArtistB-Post"));
        });

        Page<Post> page = postRepository.findFeedForFan(fanId, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getAuthorAccountId()).isEqualTo(artistA);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("ArtistA-Post");
    }

    @Test
    @DisplayName("soft-delete 처리된 PUBLISHED 포스트는 피드에 노출되지 않는다")
    void findFeedForFan_excludesSoftDeletedPost() {
        stubAccountProfile();
        String fanId = "fan-" + UUID.randomUUID();
        String artistId = "artist-" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(s -> {
            feedSubscriptionJpaRepository.save(FeedSubscription.create(fanId, artistId, Instant.now()));
            Post live = createPublishedPost(artistId, "Live-Post");
            postJpaRepository.save(live);

            Post deleted = createPublishedPost(artistId, "Deleted-Post");
            // Transition to DELETED then re-publish-status remains PUBLISHED would be invalid.
            // Instead, simulate the soft-delete invariant: PUBLISHED + deleted_at != null.
            // This requires raw mutation since the domain rejects DELETED -> PUBLISHED.
            // We use the JPA repository to persist a published post then mark deleted_at via
            // changeStatus(DELETED). After DELETED, status becomes DELETED — but the production
            // JPQL only filters status = PUBLISHED, so a DELETED post is naturally excluded.
            // To exercise the soft-delete + still-PUBLISHED bug surface, we set deleted_at
            // directly on a PUBLISHED row using JDBC-style update via JPA flush + native update
            // would break aggregate invariants.
            //
            // Simpler approach (matches Edge Case in TASK-BE-149): persist a PUBLISHED post
            // and mutate deleted_at via reflection-free path is unavailable, so we use the
            // JPA EntityManager via repository semantics. Since `Post` exposes no setDeletedAt,
            // we rely on the PostJpaRepository + transient-field-free JPA behavior: save the
            // post first, then issue a native UPDATE to set deleted_at while keeping status =
            // PUBLISHED, mirroring the corner-case the JPQL is supposed to filter.
            postJpaRepository.save(deleted);
        });

        // Native UPDATE to simulate soft-delete on the still-PUBLISHED row
        transactionTemplate.executeWithoutResult(s -> {
            postJpaRepository.findAll().stream()
                    .filter(p -> "Deleted-Post".equals(p.getTitle()))
                    .findFirst()
                    .ifPresent(p -> markDeletedAtNative(p.getId()));
        });

        Page<Post> page = postRepository.findFeedForFan(fanId, PageRequest.of(0, 20));

        // The current JPQL does not filter `deleted_at`. If this test fails, the production
        // query needs an `AND p.deletedAt IS NULL` clause.
        assertThat(page.getContent()).extracting(Post::getTitle).containsExactly("Live-Post");
    }

    @Test
    @DisplayName("PUBLISHED 포스트가 페이지 크기를 초과할 때 페이지네이션이 동작한다")
    void findFeedForFan_paginates() {
        stubAccountProfile();
        String fanId = "fan-" + UUID.randomUUID();
        String artistId = "artist-" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(s -> {
            feedSubscriptionJpaRepository.save(FeedSubscription.create(fanId, artistId, Instant.now()));
            for (int i = 0; i < 5; i++) {
                Post p = createPublishedPost(artistId, "P-" + i);
                postJpaRepository.save(p);
                // Stagger publishedAt to make ORDER BY stable.
                sleepQuietly();
            }
        });

        Page<Post> page0 = postRepository.findFeedForFan(fanId, PageRequest.of(0, 2));
        Page<Post> page1 = postRepository.findFeedForFan(fanId, PageRequest.of(1, 2));
        Page<Post> page2 = postRepository.findFeedForFan(fanId, PageRequest.of(2, 2));

        assertThat(page0.getContent()).hasSize(2);
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page2.getContent()).hasSize(1);
        assertThat(page0.getTotalElements()).isEqualTo(5);

        // Concatenated pages should not duplicate ids.
        List<String> ids = List.of(
                page0.getContent().get(0).getId(),
                page0.getContent().get(1).getId(),
                page1.getContent().get(0).getId(),
                page1.getContent().get(1).getId(),
                page2.getContent().get(0).getId());
        assertThat(ids).doesNotHaveDuplicates();
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    /**
     * Sets deleted_at on a posts row while keeping status = PUBLISHED.
     * Used to verify the JPQL soft-delete filter independent of state-machine rules.
     */
    private void markDeletedAtNative(String postId) {
        entityManager.createNativeQuery(
                        "UPDATE posts SET deleted_at = :deletedAt WHERE id = :id")
                .setParameter("deletedAt", Instant.now())
                .setParameter("id", postId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }
}
