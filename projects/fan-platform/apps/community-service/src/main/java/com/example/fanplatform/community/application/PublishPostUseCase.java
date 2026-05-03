package com.example.fanplatform.community.application;

import com.example.common.id.UuidV7;
import com.example.fanplatform.community.application.event.CommunityEventPublisher;
import com.example.fanplatform.community.application.exception.PermissionDeniedException;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import com.example.fanplatform.community.domain.post.status.PostStatusHistoryEntry;
import com.example.fanplatform.community.domain.post.status.PostStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublishPostUseCase {

    private static final String ROLE_ARTIST = "ARTIST";

    private final PostRepository postRepository;
    private final PostStatusHistoryRepository historyRepository;
    private final CommunityEventPublisher eventPublisher;
    private final PostMediaRefSerializer mediaRefSerializer;

    @Transactional
    public PostView execute(PublishPostCommand cmd) {
        ActorContext actor = cmd.actor();
        if (cmd.postType() == PostType.ARTIST_POST
                && !actor.hasRole(ROLE_ARTIST)
                && !actor.isOperator()) {
            throw new PermissionDeniedException("ARTIST role required to publish ARTIST_POST");
        }
        String mediaRefsJson = mediaRefSerializer.serialize(cmd.mediaRefs());
        String postId = UuidV7.randomString();
        Post draft = Post.createDraft(
                postId,
                actor.tenantId(),
                actor.accountId(),
                cmd.postType(),
                cmd.visibility(),
                cmd.title(),
                cmd.body(),
                mediaRefsJson
        );
        draft.publish(ActorType.AUTHOR);
        Post saved = postRepository.save(draft);

        historyRepository.save(PostStatusHistoryEntry.record(
                saved.getId(), saved.getTenantId(),
                PostStatus.DRAFT, PostStatus.PUBLISHED,
                ActorType.AUTHOR, actor.accountId(), null));

        eventPublisher.publishPostPublished(
                saved.getId(),
                saved.getTenantId(),
                saved.getAuthorAccountId(),
                saved.getPostType(),
                saved.getVisibility(),
                saved.getPublishedAt());

        return view(saved, 0L, 0L);
    }

    static PostView view(Post p, long commentCount, long reactionCount) {
        return new PostView(
                p.getId(), p.getTenantId(), p.getPostType(), p.getVisibility(), p.getStatus(),
                p.getAuthorAccountId(), p.getTitle(), p.getBody(),
                commentCount, reactionCount,
                p.getPublishedAt(), p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
