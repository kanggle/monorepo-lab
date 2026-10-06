package com.example.fanplatform.community.application;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.domain.comment.Comment;
import com.example.fanplatform.community.domain.comment.CommentRepository;
import com.example.fanplatform.community.domain.post.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paged comment listing for a post (TASK-FAN-BE-052).
 *
 * <p><strong>Visibility.</strong> Runs the SAME {@link PostAccessGuard#requirePublishedAccess}
 * gate {@code AddCommentUseCase} already runs before writing a comment — a viewer who cannot
 * read the post (not PUBLISHED and not its author, or gated by MEMBERS_ONLY/PREMIUM without the
 * required membership) must not be able to list its comments either. Reusing the exact guard
 * (rather than re-deriving the same predicate here) means the read and write paths on this
 * resource can never drift apart on who may see it.
 */
@Service
@RequiredArgsConstructor
public class GetCommentsUseCase {

    private static final int MAX_SIZE = 50;

    private final PostAccessGuard postAccessGuard;
    private final CommentRepository commentRepository;

    @Transactional(readOnly = true)
    public PageResult<Comment> execute(String postId, ActorContext actor, int page, int size) {
        Post post = postAccessGuard.requirePublishedAccess(postId, actor);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_SIZE);
        return commentRepository.findByPostId(post.getId(), actor.tenantId(), safePage, safeSize);
    }
}
