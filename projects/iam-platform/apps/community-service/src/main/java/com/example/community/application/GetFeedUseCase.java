package com.example.community.application;

import com.example.community.domain.access.AccountProfileLookup;
import com.example.community.domain.access.ContentAccessChecker;
import com.example.community.domain.comment.CommentRepository;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.reaction.ReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GetFeedUseCase {

    private static final int MAX_SIZE = 50;
    private static final int BODY_PREVIEW_MAX = 200;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReactionRepository reactionRepository;
    private final ContentAccessChecker contentAccessChecker;
    private final AccountProfileLookup accountProfileLookup;

    @Transactional(readOnly = true)
    public FeedPage execute(ActorContext actor, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_SIZE);

        Page<Post> posts = postRepository.findFeedForFan(
                actor.accountId(),
                PageRequest.of(safePage, safeSize)
        );

        List<String> postIds = posts.getContent().stream().map(Post::getId).toList();
        // Batch aggregates — fixed 2 queries regardless of page size. Empty list → empty map.
        Map<String, Long> commentCounts = commentRepository.countsByPostIds(postIds);
        Map<String, Long> reactionCounts = reactionRepository.countsByPostIds(postIds);

        List<FeedItemView> items = new ArrayList<>(posts.getNumberOfElements());
        for (Post post : posts.getContent()) {
            boolean locked = false;
            String title = post.getTitle();
            String bodyPreview = preview(post.getBody());

            if (post.getVisibility() == PostVisibility.MEMBERS_ONLY
                    && !post.getAuthorAccountId().equals(actor.accountId())) {
                boolean allowed = contentAccessChecker.check(actor.accountId(), GetPostUseCase.REQUIRED_PLAN_LEVEL);
                if (!allowed) {
                    locked = true;
                    title = null;
                    bodyPreview = null;
                }
            }

            String displayName = accountProfileLookup.displayNameOf(post.getAuthorAccountId());
            long commentCount = commentCounts.getOrDefault(post.getId(), 0L);
            long reactionCount = reactionCounts.getOrDefault(post.getId(), 0L);

            items.add(new FeedItemView(
                    post.getId(),
                    post.getType(),
                    post.getVisibility(),
                    post.getAuthorAccountId(),
                    displayName,
                    title,
                    bodyPreview,
                    commentCount,
                    reactionCount,
                    post.getPublishedAt(),
                    locked
            ));
        }

        return new FeedPage(
                items,
                posts.getNumber(),
                posts.getSize(),
                posts.getTotalElements(),
                posts.getTotalPages(),
                posts.hasNext()
        );
    }

    private static String preview(String body) {
        if (body == null) return null;
        if (body.length() <= BODY_PREVIEW_MAX) return body;
        return body.substring(0, BODY_PREVIEW_MAX);
    }
}
