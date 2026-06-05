package com.example.community.application;

import com.example.community.application.event.CommunityEventPublisher;
import com.example.community.domain.access.AccountProfileLookup;
import com.example.community.domain.comment.Comment;
import com.example.community.domain.comment.CommentRepository;
import com.example.community.domain.post.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddCommentUseCase {

    private final PostAccessGuard postAccessGuard;
    private final CommentRepository commentRepository;
    private final AccountProfileLookup accountProfileLookup;
    private final CommunityEventPublisher eventPublisher;

    public record CommentView(String commentId, String postId, String authorAccountId,
                              String authorDisplayName, String body, java.time.Instant createdAt) {}

    @Transactional
    public CommentView execute(String postId, String body, ActorContext actor) {
        Post post = postAccessGuard.requirePublishedAccess(postId, actor);

        Comment comment = Comment.create(postId, actor.accountId(), body);
        Comment saved = commentRepository.save(comment);

        eventPublisher.publishCommentCreated(
                saved.getId(),
                saved.getPostId(),
                post.getAuthorAccountId(),
                saved.getAuthorAccountId(),
                saved.getCreatedAt()
        );

        String displayName = accountProfileLookup.displayNameOf(saved.getAuthorAccountId());

        return new CommentView(
                saved.getId(),
                saved.getPostId(),
                saved.getAuthorAccountId(),
                displayName,
                saved.getBody(),
                saved.getCreatedAt()
        );
    }
}
