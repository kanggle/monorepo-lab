package com.example.fanplatform.community.application;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.domain.comment.CommentRepository;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * The feed's database reads, each in its own read-only transaction (TASK-FAN-BE-053).
 *
 * <p><strong>Why this is a separate bean and not two private methods of
 * {@link GetFeedUseCase}.</strong> The hit path overlays live counts and must survive the count
 * query failing (it falls back to the counts in the cached snapshot). That fallback is only real
 * if the failure happens <em>outside</em> any transaction the caller is still inside: Hibernate
 * marks the current transaction rollback-only the moment a query fails, so if
 * {@code GetFeedUseCase.execute} were itself {@code @Transactional}, catching the exception there
 * would still end in {@code UnexpectedRollbackException} at commit — a 500 for the very case the
 * fallback exists to absorb. With the boundary here, a failed overlay rolls back its own short
 * transaction and the use case catches a plain exception. A private method would not do: the
 * transaction proxy does not intercept self-invocation.
 *
 * <p>It also keeps the remote {@code MembershipChecker} calls outside every transaction, so no
 * pooled connection is held while waiting on membership-service.
 */
@Component
@RequiredArgsConstructor
public class FeedReader {

    private static final int BODY_PREVIEW_MAX = 200;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReactionRepository reactionRepository;

    /**
     * Miss path: the page query and both aggregates, in one transaction, exactly as before. The
     * counts baked into the snapshot are live at this moment, so the miss path needs no overlay —
     * that is what keeps it at 2 aggregate queries per page rather than 4.
     */
    @Transactional(readOnly = true)
    public PageResult<FeedItemSnapshot> buildPage(String accountId, String tenantId, int page, int size) {
        PageResult<Post> posts = postRepository.findFeedForFan(accountId, tenantId, page, size);

        List<String> postIds = posts.content().stream().map(Post::getId).toList();
        Map<String, Long> commentCounts = commentRepository.countsByPostIds(postIds, tenantId);
        Map<String, Long> reactionCounts = reactionRepository.countsByPostIds(postIds, tenantId);

        return posts.map(post -> new FeedItemSnapshot(
                post.getId(),
                post.getPostType(),
                post.getVisibility(),
                post.getAuthorAccountId(),
                post.getTitle(),
                preview(post.getBody()),
                PostMediaRefSerializer.deserialize(post.getMediaRefsJson()),
                commentCounts.getOrDefault(post.getId(), 0L),
                reactionCounts.getOrDefault(post.getId(), 0L),
                post.getPublishedAt()));
    }

    /**
     * Hit path: the two grouped aggregate queries for the posts on a cached page. One query per
     * repository regardless of page size (≤ 50 ids, {@code IN} list, served by the
     * {@code (tenant_id, post_id …)} indexes on {@code comments} and {@code reactions}).
     * The caller skips this entirely for an empty page.
     */
    @Transactional(readOnly = true)
    public LiveCounts liveCounts(List<String> postIds, String tenantId) {
        return new LiveCounts(
                commentRepository.countsByPostIds(postIds, tenantId),
                reactionRepository.countsByPostIds(postIds, tenantId));
    }

    /**
     * Counts as of now, keyed by post id. A post absent from a map has no rows — including a
     * post deleted after the page was cached — and reads as {@code 0}, never as an error.
     */
    public record LiveCounts(Map<String, Long> comments, Map<String, Long> reactions) {

        long commentsOf(String postId) {
            return comments.getOrDefault(postId, 0L);
        }

        long reactionsOf(String postId) {
            return reactions.getOrDefault(postId, 0L);
        }
    }

    private static String preview(String body) {
        if (body == null) return null;
        if (body.length() <= BODY_PREVIEW_MAX) return body;
        return body.substring(0, BODY_PREVIEW_MAX);
    }
}
