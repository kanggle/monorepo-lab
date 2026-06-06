package com.example.community.application;

import com.example.community.application.exception.MembershipRequiredException;
import com.example.community.application.exception.PostNotFoundException;
import com.example.community.domain.access.AccountProfileLookup;
import com.example.community.domain.access.ContentAccessChecker;
import com.example.community.domain.comment.CommentRepository;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.PostStatus;
import com.example.community.domain.reaction.Reaction;
import com.example.community.domain.reaction.ReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetPostUseCase {

    static final String REQUIRED_PLAN_LEVEL = "FAN_CLUB";

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReactionRepository reactionRepository;
    private final ContentAccessChecker contentAccessChecker;
    private final AccountProfileLookup accountProfileLookup;

    @Transactional(readOnly = true)
    public PostView execute(String postId, ActorContext actor) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (post.getStatus() == PostStatus.DELETED || post.getStatus() == PostStatus.DRAFT) {
            throw new PostNotFoundException(postId);
        }
        boolean isAuthor = post.getAuthorAccountId().equals(actor.accountId());
        if (post.getStatus() == PostStatus.HIDDEN && !isAuthor) {
            throw new PostNotFoundException(postId);
        }

        if (post.getVisibility() == PostVisibility.MEMBERS_ONLY && !isAuthor) {
            boolean allowed = contentAccessChecker.check(actor.accountId(), REQUIRED_PLAN_LEVEL);
            if (!allowed) {
                throw new MembershipRequiredException();
            }
        }

        long commentCount = commentRepository.countByPostId(postId);
        long reactionCount = reactionRepository.countByPostId(postId);
        String myReaction = reactionRepository.find(postId, actor.accountId())
                .map(Reaction::getEmojiCode)
                .orElse(null);
        String displayName = accountProfileLookup.displayNameOf(post.getAuthorAccountId());

        return new PostView(
                post.getId(),
                post.getType(),
                post.getVisibility(),
                post.getStatus(),
                post.getAuthorAccountId(),
                displayName,
                post.getTitle(),
                post.getBody(),
                commentCount,
                reactionCount,
                myReaction,
                post.getPublishedAt(),
                post.getCreatedAt()
        );
    }
}
