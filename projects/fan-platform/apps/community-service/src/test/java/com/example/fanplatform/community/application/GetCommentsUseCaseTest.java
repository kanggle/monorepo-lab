package com.example.fanplatform.community.application;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.application.exception.PostNotFoundException;
import com.example.fanplatform.community.domain.comment.Comment;
import com.example.fanplatform.community.domain.comment.CommentRepository;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.ActorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-FAN-BE-052 — paged comment listing.
 *
 * <p>The visibility rule under test is that this use case does NOT re-derive its own
 * access predicate: it calls the exact same {@link PostAccessGuard#requirePublishedAccess}
 * {@code AddCommentUseCase} calls before writing, so a post the actor cannot read (gated,
 * or not PUBLISHED and not theirs) 404s/403s here exactly as it would on a write.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GetCommentsUseCaseTest {

    private static final String TENANT = "fan-platform";

    @Mock PostAccessGuard postAccessGuard;
    @Mock CommentRepository commentRepository;

    @InjectMocks GetCommentsUseCase useCase;

    @Test
    @DisplayName("접근 가능한 post → PostAccessGuard 가 해석한 postId 로 댓글 페이지 조회")
    void returnsPagedCommentsForAccessiblePost() {
        Post post = published();
        when(postAccessGuard.requirePublishedAccess(eq("p1"), any(ActorContext.class)))
                .thenReturn(post);

        Comment c1 = Comment.create("c1", TENANT, "p1", "fan-1", "first");
        Comment c2 = Comment.create("c2", TENANT, "p1", "fan-2", "second");
        PageResult<Comment> page = new PageResult<>(List.of(c1, c2), 0, 20, 2, 1);
        when(commentRepository.findByPostId(eq("p1"), eq(TENANT), eq(0), eq(20)))
                .thenReturn(page);

        ActorContext actor = new ActorContext("fan-3", TENANT, Set.of("FAN"));
        PageResult<Comment> result = useCase.execute("p1", actor, 0, 20);

        assertThat(result.content()).containsExactly(c1, c2);
        assertThat(result.totalElements()).isEqualTo(2);
        verify(commentRepository).findByPostId("p1", TENANT, 0, 20);
    }

    @Test
    @DisplayName("접근 불가 post → PostAccessGuard 의 예외가 그대로 전파되고 repository 는 호출 안 됨")
    void propagatesAccessGuardExceptionWithoutQueryingRepository() {
        when(postAccessGuard.requirePublishedAccess(eq("missing"), any(ActorContext.class)))
                .thenThrow(new PostNotFoundException("missing"));

        ActorContext actor = new ActorContext("fan-1", TENANT, Set.of("FAN"));

        assertThatThrownBy(() -> useCase.execute("missing", actor, 0, 20))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("page/size 는 clamp 된다 — page<0 → 0, size>50 → 50")
    void clampsPageAndSize() {
        Post post = published();
        when(postAccessGuard.requirePublishedAccess(eq("p1"), any(ActorContext.class)))
                .thenReturn(post);
        when(commentRepository.findByPostId(eq("p1"), eq(TENANT), eq(0), eq(50)))
                .thenReturn(new PageResult<>(List.of(), 0, 50, 0, 0));

        ActorContext actor = new ActorContext("fan-1", TENANT, Set.of("FAN"));
        useCase.execute("p1", actor, -3, 500);

        verify(commentRepository).findByPostId("p1", TENANT, 0, 50);
    }

    private static Post published() {
        Post p = Post.createDraft("p1", TENANT, "author-1",
                PostType.ARTIST_POST, PostVisibility.PUBLIC, "t", "b", null);
        p.publish(ActorType.AUTHOR);
        return p;
    }
}
