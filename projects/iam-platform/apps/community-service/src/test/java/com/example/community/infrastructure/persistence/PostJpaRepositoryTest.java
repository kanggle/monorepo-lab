package com.example.community.infrastructure.persistence;

import com.example.community.domain.feed.FeedSubscription;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.ActorType;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("PostJpaRepository 쿼리 슬라이스 테스트")
class PostJpaRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("community_db")
            .withUsername("test")
            .withPassword("test")
            .withCommand("mysqld", "--log-bin-trust-function-creators=1")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private PostJpaRepository postRepo;

    @Autowired
    private FeedSubscriptionJpaRepository feedRepo;

    @BeforeEach
    void cleanup() {
        postRepo.deleteAll();
        feedRepo.deleteAll();
    }

    // ── findFeedForFan ────────────────────────────────────────────────────────

    @Test
    @DisplayName("findFeedForFan — 구독한 아티스트의 발행 포스트 반환")
    void findFeedForFan_subscribedArtistPublishedPost_returnsPost() {
        String fan = uuid(), artist = uuid();
        feedRepo.saveAndFlush(FeedSubscription.create(fan, artist, Instant.now()));
        Post post = Post.createDraft(artist, PostType.ARTIST_POST, PostVisibility.PUBLIC, "Title", "Body", null);
        post.publish(ActorType.AUTHOR);
        postRepo.saveAndFlush(post);

        Page<Post> result = postRepo.findFeedForFan(fan, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getAuthorAccountId()).isEqualTo(artist);
    }

    @Test
    @DisplayName("findFeedForFan — DRAFT 포스트는 피드에서 제외")
    void findFeedForFan_draftPost_excluded() {
        String fan = uuid(), artist = uuid();
        feedRepo.saveAndFlush(FeedSubscription.create(fan, artist, Instant.now()));
        postRepo.saveAndFlush(
                Post.createDraft(artist, PostType.ARTIST_POST, PostVisibility.PUBLIC, "Title", "Body", null));

        Page<Post> result = postRepo.findFeedForFan(fan, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("findFeedForFan — 미구독 아티스트 포스트는 피드에서 제외")
    void findFeedForFan_unsubscribedArtist_excluded() {
        String fan = uuid(), artist = uuid(), other = uuid();
        feedRepo.saveAndFlush(FeedSubscription.create(fan, artist, Instant.now()));
        Post post = Post.createDraft(other, PostType.ARTIST_POST, PostVisibility.PUBLIC, "Title", "Body", null);
        post.publish(ActorType.AUTHOR);
        postRepo.saveAndFlush(post);

        Page<Post> result = postRepo.findFeedForFan(fan, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("findFeedForFan — 구독 없는 팬 → 빈 피드 반환")
    void findFeedForFan_noSubscriptions_returnsEmpty() {
        Page<Post> result = postRepo.findFeedForFan(uuid(), PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
