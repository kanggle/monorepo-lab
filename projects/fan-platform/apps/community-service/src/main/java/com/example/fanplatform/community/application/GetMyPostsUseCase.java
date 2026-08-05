package com.example.fanplatform.community.application;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.domain.comment.CommentRepository;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * The author's own posts, newest first (TASK-FAN-FE-016).
 *
 * <p><strong>Why this exists.</strong> Publishing worked and returned 201, but there was no
 * path in the product back to what you published: the feed is follow-based, so a fan's own
 * post never lands in their own feed, and {@code GET /{id}} needs an id nobody had written
 * down. Two gaps hid each other — you could not write a post, and if you had, you could not
 * have found it. Adding a compose screen without this would have shipped the second half of
 * that pair.
 *
 * <p><strong>No visibility gate, deliberately.</strong> {@link GetPostUseCase} runs
 * {@code PostAccessGuard}, but it also short-circuits on {@code actor.owns(...)} first. Here
 * the caller IS the author for every row by construction, so applying the gate could only ever
 * do one of two things: nothing, or wrongly hide a post from the person who wrote it. The
 * scope is the guard.
 */
@Service
@RequiredArgsConstructor
public class GetMyPostsUseCase {

    private static final int MAX_SIZE = 50;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReactionRepository reactionRepository;

    @Transactional(readOnly = true)
    public PageResult<PostView> execute(ActorContext actor, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_SIZE);

        PageResult<Post> posts = postRepository.findByAuthor(
                actor.accountId(), actor.tenantId(), safePage, safeSize);

        List<String> postIds = posts.content().stream().map(Post::getId).toList();
        Map<String, Long> commentCounts = commentRepository.countsByPostIds(postIds, actor.tenantId());
        Map<String, Long> reactionCounts = reactionRepository.countsByPostIds(postIds, actor.tenantId());

        return posts.map(post -> PublishPostUseCase.view(
                post,
                commentCounts.getOrDefault(post.getId(), 0L),
                reactionCounts.getOrDefault(post.getId(), 0L)));
    }
}
