package com.example.community.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import com.example.community.domain.reaction.Reaction;
import com.example.community.infrastructure.persistence.ReactionJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository slice integration tests for {@link ReactionJpaRepository} (TASK-BE-154).
 *
 * <p>Validates: 복합 PK 조회, (post_id, account_id) UNIQUE 제약, 카운트 쿼리.
 */
@Tag("integration")
@SpringBootTest
@DisplayName("ReactionJpaRepository 통합 테스트")
class ReactionJpaRepositoryIntegrationTest extends CommunityIntegrationTestBase {

    @Autowired
    private ReactionJpaRepository reactionRepo;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        reactionRepo.deleteAll();
    }

    @Test
    @DisplayName("findByPostIdAndAccountId — 존재하는 리액션 조회")
    void findByPostIdAndAccountId_existing_returnsReaction() {
        String postId = "post-" + UUID.randomUUID().toString().substring(0, 20);
        String accountId = "account-" + UUID.randomUUID().toString().substring(0, 20);

        transactionTemplate.executeWithoutResult(s ->
                reactionRepo.save(Reaction.create(postId, accountId, "HEART")));

        Optional<Reaction> result = reactionRepo.findByPostIdAndAccountId(postId, accountId);

        assertThat(result).isPresent();
        assertThat(result.get().getEmojiCode()).isEqualTo("HEART");
    }

    @Test
    @DisplayName("findByPostIdAndAccountId — 없는 키 → empty")
    void findByPostIdAndAccountId_unknown_returnsEmpty() {
        Optional<Reaction> result = reactionRepo.findByPostIdAndAccountId(
                "ghost-post-" + UUID.randomUUID().toString().substring(0, 20),
                "ghost-account-" + UUID.randomUUID().toString().substring(0, 20));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("복합 PK — (post_id, account_id) 중복 INSERT → DataIntegrityViolationException")
    void compositeKey_uniqueConstraint_secondInsertFails() {
        String postId = "post-" + UUID.randomUUID().toString().substring(0, 20);
        String accountId = "account-" + UUID.randomUUID().toString().substring(0, 20);

        transactionTemplate.executeWithoutResult(s ->
                reactionRepo.save(Reaction.create(postId, accountId, "HEART")));

        // TASK-MONO-044c: JpaRepository.save() on an entity with the same composite-PK
        // performs MERGE (load-then-update), not INSERT, so it never raises a duplicate-
        // key violation. To exercise the DB-level UNIQUE/PK constraint we must issue
        // a raw INSERT via JdbcTemplate.
        Instant now = Instant.now();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO reactions (post_id, account_id, emoji_code, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                postId, accountId, "FIRE",
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("countByPostId — 동일 포스트의 리액션 수 정확히 반환")
    void countByPostId_returnsAccurateCount() {
        String postId = "post-" + UUID.randomUUID().toString().substring(0, 20);

        transactionTemplate.executeWithoutResult(s -> {
            reactionRepo.save(Reaction.create(postId, "user-1-" + UUID.randomUUID().toString().substring(0, 20), "HEART"));
            reactionRepo.save(Reaction.create(postId, "user-2-" + UUID.randomUUID().toString().substring(0, 20), "FIRE"));
            reactionRepo.save(Reaction.create(postId, "user-3-" + UUID.randomUUID().toString().substring(0, 20), "HEART"));
        });

        long count = reactionRepo.countByPostId(postId);

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("countsGroupedByPostId — 여러 포스트의 그룹별 카운트 반환")
    void countsGroupedByPostId_returnsPerPostCounts() {
        String postA = "post-a-" + UUID.randomUUID().toString().substring(0, 20);
        String postB = "post-b-" + UUID.randomUUID().toString().substring(0, 20);

        transactionTemplate.executeWithoutResult(s -> {
            reactionRepo.save(Reaction.create(postA, "user-1-" + UUID.randomUUID().toString().substring(0, 20), "HEART"));
            reactionRepo.save(Reaction.create(postA, "user-2-" + UUID.randomUUID().toString().substring(0, 20), "FIRE"));
            reactionRepo.save(Reaction.create(postB, "user-3-" + UUID.randomUUID().toString().substring(0, 20), "HEART"));
        });

        Map<String, Long> counts = reactionRepo
                .countsGroupedByPostId(List.of(postA, postB))
                .stream()
                .collect(Collectors.toMap(
                        ReactionJpaRepository.PostIdCount::getPostId,
                        ReactionJpaRepository.PostIdCount::getCnt));

        assertThat(counts).containsEntry(postA, 2L);
        assertThat(counts).containsEntry(postB, 1L);
    }
}
