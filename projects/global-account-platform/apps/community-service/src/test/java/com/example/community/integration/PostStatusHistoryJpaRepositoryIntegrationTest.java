package com.example.community.integration;

import org.junit.jupiter.api.Tag;
import com.example.community.infrastructure.persistence.PostStatusHistoryJpaEntity;
import com.example.community.infrastructure.persistence.PostStatusHistoryJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository slice integration tests for {@link PostStatusHistoryJpaRepository} (TASK-BE-154).
 *
 * <p>Validates: 시간 오름차순 정렬, append-only DB 트리거 (UPDATE/DELETE 거부).
 */
@Tag("integration")
@SpringBootTest
@DisplayName("PostStatusHistoryJpaRepository 통합 테스트")
class PostStatusHistoryJpaRepositoryIntegrationTest extends CommunityIntegrationTestBase {

    @Autowired
    private PostStatusHistoryJpaRepository historyRepo;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("findByPostIdOrderByOccurredAtAsc — occurred_at 오름차순으로 반환")
    void findByPostIdOrderByOccurredAtAsc_returnsAscOrder() {
        String postId = "post-" + UUID.randomUUID();
        String authorId = "author-" + UUID.randomUUID();

        // 두 transition: DRAFT→PUBLISHED, PUBLISHED→DELETED (시간 차이를 위해 stagger)
        transactionTemplate.executeWithoutResult(s -> {
            historyRepo.save(PostStatusHistoryJpaEntity.record(
                    postId, "DRAFT", "PUBLISHED", "AUTHOR", authorId, null));
            sleepQuietly();
            historyRepo.save(PostStatusHistoryJpaEntity.record(
                    postId, "PUBLISHED", "DELETED", "AUTHOR", authorId, "user-request"));
        });

        List<PostStatusHistoryJpaEntity> result = historyRepo.findByPostIdOrderByOccurredAtAsc(postId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getToStatus()).isEqualTo("PUBLISHED");
        assertThat(result.get(1).getToStatus()).isEqualTo("DELETED");
        assertThat(result.get(0).getOccurredAt()).isBeforeOrEqualTo(result.get(1).getOccurredAt());
    }

    @Test
    @DisplayName("트리거 — post_status_history UPDATE 시도 → 예외 (append-only)")
    void appendOnlyTrigger_update_throwsException() {
        String postId = "post-" + UUID.randomUUID();

        PostStatusHistoryJpaEntity saved = transactionTemplate.execute(s ->
                historyRepo.save(PostStatusHistoryJpaEntity.record(
                        postId, "DRAFT", "PUBLISHED", "AUTHOR", "author-x", null)));

        assertThatThrownBy(() ->
                jdbc.update("UPDATE post_status_history SET actor_type = 'TAMPERED' WHERE id = ?",
                        saved.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("트리거 — post_status_history DELETE 시도 → 예외 (append-only)")
    void appendOnlyTrigger_delete_throwsException() {
        String postId = "post-" + UUID.randomUUID();

        PostStatusHistoryJpaEntity saved = transactionTemplate.execute(s ->
                historyRepo.save(PostStatusHistoryJpaEntity.record(
                        postId, "DRAFT", "PUBLISHED", "AUTHOR", "author-x", null)));

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM post_status_history WHERE id = ?", saved.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
