package com.example.community.integration;

import com.example.community.domain.comment.Comment;
import com.example.community.infrastructure.persistence.CommentJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice integration tests for {@link CommentJpaRepository} (TASK-BE-154).
 *
 * <p>Validates: soft-delete 제외 카운트, 그룹별 카운트.
 */
@SpringBootTest
@DisplayName("CommentJpaRepository 통합 테스트")
class CommentJpaRepositoryIntegrationTest extends CommunityIntegrationTestBase {

    @Autowired
    private CommentJpaRepository commentRepo;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("countByPostIdAndDeletedAtIsNull — 활성 댓글만 카운트, soft-delete 제외")
    void countByPostIdAndDeletedAtIsNull_includesActiveExcludesDeleted() {
        String postId = "post-" + UUID.randomUUID();
        String authorId = "author-" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(s -> {
            commentRepo.save(Comment.create(postId, authorId, "active-1"));
            commentRepo.save(Comment.create(postId, authorId, "active-2"));
            Comment toDelete = commentRepo.save(Comment.create(postId, authorId, "to-delete"));
            // soft-delete via native UPDATE (도메인이 직접 노출하지 않음)
            jdbc.update("UPDATE comments SET deleted_at = ? WHERE id = ?",
                    Instant.now().toString(), toDelete.getId());
        });

        long count = commentRepo.countByPostIdAndDeletedAtIsNull(postId);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("countsGroupedByPostId — 여러 포스트의 그룹별 카운트 반환")
    void countsGroupedByPostId_returnsPerPostCounts() {
        String postA = "post-a-" + UUID.randomUUID();
        String postB = "post-b-" + UUID.randomUUID();
        String authorId = "author-" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(s -> {
            commentRepo.save(Comment.create(postA, authorId, "A-1"));
            commentRepo.save(Comment.create(postA, authorId, "A-2"));
            commentRepo.save(Comment.create(postA, authorId, "A-3"));
            commentRepo.save(Comment.create(postB, authorId, "B-1"));
        });

        Map<String, Long> counts = commentRepo
                .countsGroupedByPostId(List.of(postA, postB))
                .stream()
                .collect(Collectors.toMap(
                        CommentJpaRepository.PostIdCount::getPostId,
                        CommentJpaRepository.PostIdCount::getCnt));

        assertThat(counts).containsEntry(postA, 3L);
        assertThat(counts).containsEntry(postB, 1L);
    }

    @Test
    @DisplayName("countsGroupedByPostId — soft-delete 행은 그룹 카운트에서 제외")
    void countsGroupedByPostId_excludesSoftDeleted() {
        String postId = "post-" + UUID.randomUUID();
        String authorId = "author-" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(s -> {
            commentRepo.save(Comment.create(postId, authorId, "live-1"));
            Comment deleted = commentRepo.save(Comment.create(postId, authorId, "deleted-1"));
            jdbc.update("UPDATE comments SET deleted_at = ? WHERE id = ?",
                    Instant.now().toString(), deleted.getId());
        });

        Map<String, Long> counts = commentRepo
                .countsGroupedByPostId(List.of(postId))
                .stream()
                .collect(Collectors.toMap(
                        CommentJpaRepository.PostIdCount::getPostId,
                        CommentJpaRepository.PostIdCount::getCnt));

        assertThat(counts).containsEntry(postId, 1L);
    }
}
