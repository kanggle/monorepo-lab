package com.example.community.infrastructure.persistence;

import com.example.testsupport.integration.DockerAvailableCondition;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("PostStatusHistoryJpaRepository 쿼리 슬라이스 테스트")
class PostStatusHistoryJpaRepositoryTest {

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
    private PostStatusHistoryJpaRepository repo;

    @Autowired
    private JdbcTemplate jdbc;

    // No @BeforeEach cleanup — post_status_history is append-only (DB triggers prevent DELETE)
    // Test isolation via unique postId per test

    // ── findByPostIdOrderByOccurredAtAsc ──────────────────────────────────────

    @Test
    @DisplayName("findByPostIdOrderByOccurredAtAsc — 발생 시간 오름차순으로 이력 반환")
    void findByPostIdOrderByOccurredAtAsc_multipleEntries_returnsSortedAsc() {
        String postId = uuid();
        Instant t1 = Instant.now().minusSeconds(10);
        Instant t2 = Instant.now().minusSeconds(5);
        Instant t3 = Instant.now();
        // Insert in reverse order to verify the ORDER BY clause works
        insertHistory(postId, "PUBLISHED", "HIDDEN", t3);
        insertHistory(postId, "DRAFT", "PUBLISHED", t1);
        insertHistory(postId, "HIDDEN", "DELETED", t2);

        List<PostStatusHistoryJpaEntity> result =
                repo.findByPostIdOrderByOccurredAtAsc(postId);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getFromStatus()).isEqualTo("DRAFT");
        assertThat(result.get(1).getFromStatus()).isEqualTo("HIDDEN");
        assertThat(result.get(2).getFromStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("findByPostIdOrderByOccurredAtAsc — 이력 없는 포스트 → 빈 목록")
    void findByPostIdOrderByOccurredAtAsc_noHistory_returnsEmpty() {
        assertThat(repo.findByPostIdOrderByOccurredAtAsc(uuid())).isEmpty();
    }

    @Test
    @DisplayName("findByPostIdOrderByOccurredAtAsc — 다른 포스트 이력은 반환하지 않음")
    void findByPostIdOrderByOccurredAtAsc_excludesOtherPostHistory() {
        String postA = uuid(), postB = uuid();
        repo.saveAndFlush(PostStatusHistoryJpaEntity.record(postA, "DRAFT", "PUBLISHED", "AUTHOR", uuid(), null));
        repo.saveAndFlush(PostStatusHistoryJpaEntity.record(postB, "DRAFT", "PUBLISHED", "AUTHOR", uuid(), null));

        List<PostStatusHistoryJpaEntity> result =
                repo.findByPostIdOrderByOccurredAtAsc(postA);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPostId()).isEqualTo(postA);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void insertHistory(String postId, String fromStatus, String toStatus, Instant occurredAt) {
        jdbc.update(
                "INSERT INTO post_status_history (post_id, from_status, to_status, actor_type, actor_id, reason, occurred_at)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?)",
                postId, fromStatus, toStatus, "AUTHOR", null, null,
                java.sql.Timestamp.from(occurredAt));
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
