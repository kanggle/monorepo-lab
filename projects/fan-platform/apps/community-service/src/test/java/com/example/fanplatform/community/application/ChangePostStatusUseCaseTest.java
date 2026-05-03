package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.event.CommunityEventPublisher;
import com.example.fanplatform.community.application.exception.PermissionDeniedException;
import com.example.fanplatform.community.application.exception.PostNotFoundException;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.domain.post.status.InvalidStateTransitionException;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import com.example.fanplatform.community.domain.post.status.PostStatusHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ChangePostStatusUseCaseTest {

    private static final String TENANT = "fan-platform";

    @Mock PostRepository postRepository;
    @Mock PostStatusHistoryRepository historyRepository;
    @Mock CommunityEventPublisher eventPublisher;

    @InjectMocks ChangePostStatusUseCase useCase;

    @Test
    @DisplayName("PUBLISHED → DRAFT 전이 시도 → InvalidStateTransitionException")
    void publishedToDraftRejected() {
        Post post = published();
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.of(post));

        ActorContext actor = new ActorContext("author-1", TENANT, Set.of("FAN"));
        assertThatThrownBy(() -> useCase.execute("p1", PostStatus.DRAFT, actor, null))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("작성자 외 일반 actor 가 transition 시도 → PermissionDeniedException")
    void nonAuthorRejected() {
        Post post = published();
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.of(post));

        ActorContext actor = new ActorContext("stranger", TENANT, Set.of("FAN"));
        assertThatThrownBy(() -> useCase.execute("p1", PostStatus.HIDDEN, actor, null))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("작성자 본인의 PUBLISHED→HIDDEN 전이 → success + 이벤트 발행")
    void authorCanHide() {
        Post post = published();
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        ActorContext actor = new ActorContext("author-1", TENANT, Set.of("FAN"));
        useCase.execute("p1", PostStatus.HIDDEN, actor, "test");

        verify(historyRepository).save(any());
        verify(eventPublisher).publishPostStatusChanged(
                any(String.class), any(String.class),
                any(PostStatus.class), any(PostStatus.class),
                any(String.class), any());
    }

    @Test
    @DisplayName("Cross-tenant 조회 → PostNotFoundException")
    void crossTenantNotFound() {
        when(postRepository.findById("p1", TENANT)).thenReturn(Optional.empty());

        ActorContext actor = new ActorContext("ops", TENANT, Set.of("OPERATOR"));
        assertThatThrownBy(() -> useCase.execute("p1", PostStatus.HIDDEN, actor, null))
                .isInstanceOf(PostNotFoundException.class);
    }

    private static Post published() {
        Post p = Post.createDraft("p1", TENANT, "author-1",
                PostType.ARTIST_POST, PostVisibility.PUBLIC, "t", "b", null);
        p.publish(ActorType.AUTHOR);
        return p;
    }
}
