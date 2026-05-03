package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.exception.PostNotFoundException;
import com.example.fanplatform.community.domain.comment.CommentRepository;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetPostUseCase {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReactionRepository reactionRepository;
    private final PostAccessGuard postAccessGuard;

    @Transactional(readOnly = true)
    public PostView execute(String postId, ActorContext actor) {
        Post post = postRepository.findById(postId, actor.tenantId())
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (post.getStatus() == PostStatus.DELETED) {
            throw new PostNotFoundException(postId);
        }
        boolean isAuthor = post.getAuthorAccountId().equals(actor.accountId());
        if (post.getStatus() == PostStatus.HIDDEN && !isAuthor && !actor.isOperator()) {
            throw new PostNotFoundException(postId);
        }
        if (post.getStatus() == PostStatus.DRAFT && !isAuthor && !actor.isOperator()) {
            throw new PostNotFoundException(postId);
        }
        postAccessGuard.ensureVisibilityAccessible(post, actor);

        long commentCount = commentRepository.countByPostId(postId, actor.tenantId());
        long reactionCount = reactionRepository.countByPostId(postId, actor.tenantId());
        return PublishPostUseCase.view(post, commentCount, reactionCount);
    }
}
