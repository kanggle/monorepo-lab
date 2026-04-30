package com.example.community.infrastructure.persistence;

import com.example.community.domain.comment.Comment;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("CommentJpaRepository 쿼리 슬라이스 테스트")
class CommentJpaRepositoryTest {

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
    private CommentJpaRepository repo;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanup() {
        jdbc.update("DELETE FROM comments");
    }

    // ── countByPostIdAndDeletedAtIsNull ───────────────────────────────────────

    @Test
    @DisplayName("countByPostIdAndDeletedAtIsNull — 소프트 삭제된 댓글 제외하고 카운트 반환")
    void countByPostIdAndDeletedAtIsNull_excludesSoftDeleted() {
        String postId = uuid();
        repo.saveAndFlush(Comment.create(postId, uuid(), "active 1"));
        repo.saveAndFlush(Comment.create(postId, uuid(), "active 2"));
        insertSoftDeletedComment(postId);

        assertThat(repo.countByPostIdAndDeletedAtIsNull(postId)).isEqualTo(2);
    }

    @Test
    @DisplayName("countByPostIdAndDeletedAtIsNull — 댓글 없는 포스트 → 0 반환")
    void countByPostIdAndDeletedAtIsNull_noComments_returnsZero() {
        assertThat(repo.countByPostIdAndDeletedAtIsNull(uuid())).isZero();
    }

    // ── countsGroupedByPostId ─────────────────────────────────────────────────

    @Test
    @DisplayName("countsGroupedByPostId — 포스트별 활성 댓글 수 반환")
    void countsGroupedByPostId_multiplePostIds_returnsPerPostCount() {
        String postA = uuid(), postB = uuid();
        repo.saveAndFlush(Comment.create(postA, uuid(), "a1"));
        repo.saveAndFlush(Comment.create(postA, uuid(), "a2"));
        repo.saveAndFlush(Comment.create(postB, uuid(), "b1"));

        List<CommentJpaRepository.PostIdCount> result =
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

    @Test
    @DisplayName("countsGroupedByPostId — 소프트 삭제된 댓글은 카운트에서 제외")
    void countsGroupedByPostId_excludesSoftDeletedComments() {
        String postId = uuid();
        repo.saveAndFlush(Comment.create(postId, uuid(), "active"));
        insertSoftDeletedComment(postId);

        List<CommentJpaRepository.PostIdCount> result = repo.countsGroupedByPostId(List.of(postId));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCnt()).isEqualTo(1L);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void insertSoftDeletedComment(String postId) {
        jdbc.update(
                "INSERT INTO comments (id, post_id, author_account_id, body, created_at, deleted_at)" +
                " VALUES (?, ?, ?, ?, NOW(6), NOW(6))",
                uuid(), postId, uuid(), "deleted comment");
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
