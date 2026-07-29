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
import java.util.List;

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
        // Recipient-routing fields (TASK-FAN-BE-026): the post author is the
        // reply-alert recipient, resolved from the Post the access guard already
        // loaded in this transaction — no extra read, no consumer callback.
        //
        // mentionedAccountIds is ALWAYS EMPTY today: community-service has no
        // @-mention syntax (AddCommentRequest carries only `body`) and no
        // username→accountId directory to resolve one against. The field is
        // populated as an empty list so the contract shape is stable on the wire;
        // filling it is a producer-only change once a mention-resolution feature
        // exists (out of scope for TASK-FAN-BE-026).
        eventPublisher.publishCommentAdded(
                saved.getId(), saved.getPostId(), saved.getTenantId(),
                saved.getAuthorAccountId(), post.getAuthorAccountId(),
                List.of(), saved.getCreatedAt());
        return new CommentView(saved.getId(), saved.getPostId(), saved.getTenantId(),
                saved.getAuthorAccountId(), saved.getBody(), saved.getCreatedAt());
    }
}
