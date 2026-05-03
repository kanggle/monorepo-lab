package com.example.fanplatform.community.application;

import com.example.common.id.UuidV7;
import com.example.fanplatform.community.application.event.CommunityEventPublisher;
import com.example.fanplatform.community.domain.comment.Comment;
import com.example.fanplatform.community.domain.comment.CommentRepository;
import com.example.fanplatform.community.domain.post.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AddCommentUseCase {

    private final PostAccessGuard postAccessGuard;
    private final CommentRepository commentRepository;
    private final CommunityEventPublisher eventPublisher;

    public record CommentView(String commentId, String postId, String tenantId,
                              String authorAccountId, String body, Instant createdAt) {}

    @Transactional
    public CommentView execute(String postId, String body, ActorContext actor) {
        Post post = postAccessGuard.requirePublishedAccess(postId, actor);
        String commentId = UuidV7.randomString();
        Comment comment = Comment.create(commentId, actor.tenantId(),
                post.getId(), actor.accountId(), body);
        Comment saved = commentRepository.save(comment);
        eventPublisher.publishCommentAdded(
                saved.getId(), saved.getPostId(), saved.getTenantId(),
                saved.getAuthorAccountId(), saved.getCreatedAt());
        return new CommentView(saved.getId(), saved.getPostId(), saved.getTenantId(),
                saved.getAuthorAccountId(), saved.getBody(), saved.getCreatedAt());
    }
}
