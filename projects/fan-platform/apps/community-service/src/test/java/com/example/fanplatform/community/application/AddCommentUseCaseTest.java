package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.event.CommunityEventPublisher;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-FAN-BE-026 — the emitted {@code community.comment.added} carries the
 * recipient-routing fields so notification-service never has to call back here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AddCommentUseCaseTest {

    private static final String TENANT = "fan-platform";

    @Mock PostAccessGuard postAccessGuard;
    @Mock CommentRepository commentRepository;
    @Mock CommunityEventPublisher eventPublisher;

    @InjectMocks AddCommentUseCase useCase;

    @Test
    @DisplayName("댓글 작성 → postAuthorAccountId 가 실린 comment.added 이벤트 발행")
    void publishesCommentAddedWithPostAuthor() {
        when(postAccessGuard.requirePublishedAccess(eq("p1"), any(ActorContext.class)))
                .thenReturn(published());
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ActorContext actor = new ActorContext("fan-1", TENANT, Set.of("FAN"));
        AddCommentUseCase.CommentView view = useCase.execute("p1", "nice post", actor);

        assertThat(view.postId()).isEqualTo("p1");
        assertThat(view.authorAccountId()).isEqualTo("fan-1");

        verify(eventPublisher).publishCommentAdded(
                eq(view.commentId()), eq("p1"), eq(TENANT),
                eq("fan-1"),      // comment author = the actor
                eq("author-1"),   // post author = the reply-alert recipient
                eq(List.of()),    // mentions: always empty (no mention syntax exists)
                any(Instant.class));
    }

    @Test
    @DisplayName("자기 글에 댓글 → actor 와 postAuthorAccountId 가 같게 실린다 (억제는 consumer 책임)")
    void selfCommentCarriesIdenticalActorAndPostAuthor() {
        when(postAccessGuard.requirePublishedAccess(eq("p1"), any(ActorContext.class)))
                .thenReturn(published()); // author-1
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ActorContext actor = new ActorContext("author-1", TENANT, Set.of("FAN"));
        useCase.execute("p1", "self comment", actor);

        verify(eventPublisher).publishCommentAdded(
                any(String.class), eq("p1"), eq(TENANT),
                eq("author-1"), eq("author-1"), eq(List.of()), any(Instant.class));
    }

    @Test
    @DisplayName("댓글은 저장 후 발행 — 저장된 comment 의 식별자/시각이 이벤트에 실린다")
    void publishesTheSavedCommentIdentity() {
        when(postAccessGuard.requirePublishedAccess(eq("p1"), any(ActorContext.class)))
                .thenReturn(published());
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ActorContext actor = new ActorContext("fan-1", TENANT, Set.of("FAN"));
        useCase.execute("p1", "body", actor);

        org.mockito.ArgumentCaptor<Comment> captor =
                org.mockito.ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        Comment saved = captor.getValue();

        verify(eventPublisher).publishCommentAdded(
                eq(saved.getId()), eq(saved.getPostId()), eq(saved.getTenantId()),
                eq(saved.getAuthorAccountId()), eq("author-1"), eq(List.of()),
                eq(saved.getCreatedAt()));
    }

    private static Post published() {
        Post p = Post.createDraft("p1", TENANT, "author-1",
                PostType.ARTIST_POST, PostVisibility.PUBLIC, "t", "b", null);
        p.publish(ActorType.AUTHOR);
        return p;
    }
}
