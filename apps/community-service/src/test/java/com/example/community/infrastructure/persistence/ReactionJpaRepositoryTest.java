package com.example.community.infrastructure.persistence;

import com.example.community.domain.reaction.Reaction;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("ReactionJpaRepository 쿼리 슬라이스 테스트")
class ReactionJpaRepositoryTest {

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
    private ReactionJpaRepository repo;

    @BeforeEach
    void cleanup() {
        repo.deleteAll();
    }

    // ── findByPostIdAndAccountId ──────────────────────────────────────────────

    @Test
    @DisplayName("findByPostIdAndAccountId — 기존 반응 반환")
    void findByPostIdAndAccountId_existing_returnsReaction() {
        String postId = uuid(), accountId = uuid();
        repo.saveAndFlush(Reaction.create(postId, accountId, "heart"));

        Optional<Reaction> result = repo.findByPostIdAndAccountId(postId, accountId);

        assertThat(result).isPresent();
        assertThat(result.get().getEmojiCode()).isEqualTo("heart");
    }

    @Test
    @DisplayName("findByPostIdAndAccountId — 존재하지 않는 반응 → empty")
    void findByPostIdAndAccountId_notFound_returnsEmpty() {
        assertThat(repo.findByPostIdAndAccountId(uuid(), uuid())).isEmpty();
    }

    // ── countByPostId ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("countByPostId — 포스트의 반응 수 반환")
    void countByPostId_returnsCount() {
        String postId = uuid();
        repo.saveAndFlush(Reaction.create(postId, uuid(), "heart"));
        repo.saveAndFlush(Reaction.create(postId, uuid(), "thumbsup"));

        assertThat(repo.countByPostId(postId)).isEqualTo(2);
    }

    // ── countsGroupedByPostId ─────────────────────────────────────────────────

    @Test
    @DisplayName("countsGroupedByPostId — 포스트별 반응 수 그룹 반환")
    void countsGroupedByPostId_multiplePostIds_returnsGroupedCounts() {
        String postA = uuid(), postB = uuid();
        repo.saveAndFlush(Reaction.create(postA, uuid(), "heart"));
        repo.saveAndFlush(Reaction.create(postA, uuid(), "thumbsup"));
        repo.saveAndFlush(Reaction.create(postB, uuid(), "heart"));

        List<ReactionJpaRepository.PostIdCount> result =
                repo.countsGroupedByPostId(List.of(postA, postB));

        assertThat(result).hasSize(2);
        assertThat(result)
                .anySatisfy(r -> {
                    assertThat(r.getPostId()).isEqualTo(postA);
                    assertThat(r.getCnt()).isEqualTo(2L);
                })
                .anySatisfy(r -> {
                    assertThat(r.getPostId()).isEqualTo(postB);
                    assertThat(r.getCnt()).isEqualTo(1L);
                });
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
